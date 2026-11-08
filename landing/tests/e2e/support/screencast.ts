import fs from 'node:fs/promises';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import type { CDPSession, Page } from '@playwright/test';

/** A CDP screencast with real timestamps (Playwright's own video needs a bundled ffmpeg that is not installed here; the system ffmpeg turns the frames into an mp4). */
export type Cast = { frames: Array<{ ts: number; data: string }>; stop: () => Promise<void>; cdp: CDPSession };

export async function startCast(page: Page, width = 1672, height = 941): Promise<Cast> {
  const cdp = await page.context().newCDPSession(page);
  const frames: Cast['frames'] = [];
  cdp.on('Page.screencastFrame', async (event) => {
    frames.push({ ts: event.metadata.timestamp ?? 0, data: event.data });
    try { await cdp.send('Page.screencastFrameAck', { sessionId: event.sessionId }); } catch { /* the page is closing */ }
  });
  await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 88, everyNthFrame: 1, maxWidth: width, maxHeight: height });
  return { frames, cdp, stop: async () => { await cdp.send('Page.stopScreencast'); } };
}

/** Writes the picked frames (the nearest frame to each millisecond mark after `t0`, epoch seconds) and the whole recording as an mp4. */
export async function saveCast(cast: Cast, t0: number, dir: string, name: string, picks: number[]) {
  await fs.mkdir(dir, { recursive: true });
  const list = cast.frames.map((frame) => ({ ms: (frame.ts - t0) * 1000, data: frame.data })).filter((frame) => frame.ms >= -50).sort((a, b) => a.ms - b.ms);
  const scratch = path.join(dir, `.${name}-frames`); await fs.mkdir(scratch, { recursive: true });
  let concat = '';
  for (let i = 0; i < list.length; i++) {
    const file = path.join(scratch, `f${String(i).padStart(5, '0')}.jpg`); await fs.writeFile(file, Buffer.from(list[i].data, 'base64'));
    const duration = i + 1 < list.length ? Math.max(0.001, (list[i + 1].ms - list[i].ms) / 1000) : 0.04;
    concat += `file '${file}'\nduration ${duration.toFixed(4)}\n`;
  }
  await fs.writeFile(path.join(scratch, 'list.txt'), concat);
  const picked: Array<[number, number]> = [];
  for (const ms of picks) {
    const best = list.reduce((a, b) => (Math.abs(b.ms - ms) < Math.abs(a.ms - ms) ? b : a));
    await fs.writeFile(path.join(dir, `${name}-${String(ms).padStart(5, '0')}ms.jpg`), Buffer.from(best.data, 'base64')); picked.push([ms, Math.round(best.ms)]);
  }
  execFileSync('ffmpeg', ['-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', path.join(scratch, 'list.txt'), '-vf', 'fps=30,scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p', '-c:v', 'libx264', '-crf', '24', path.join(dir, `${name}.mp4`)]);
  await fs.rm(scratch, { recursive: true, force: true });
  return { frames: list.length, picked, spanMs: list.length ? [Math.round(list[0].ms), Math.round(list.at(-1)!.ms)] : [] };
}
