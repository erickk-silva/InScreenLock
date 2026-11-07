// Renders the static WebGL-fallback poster from the live 3D scene itself, so the poster and the model can never drift apart.
// Usage: npm run build && npm run render-phone-poster      (env: BASE=<url of a running site>, E2E_GPU=1 for the hardware GPU)
// The scene is opened in its settled state (?visualTest=1: final pose, final screen, no idle) and the canvas pixels, with their alpha, are saved as WebP.
import fs from 'node:fs/promises';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const gpu = process.env.E2E_GPU === '1';
const port = 3110;
let server = null;
let base = process.env.BASE;
if (!base) {
  server = spawn('node', [path.join(root, 'scripts/serve-export.mjs'), '--port', String(port)], { stdio: 'ignore' });
  base = `http://127.0.0.1:${port}`;
  await new Promise((resolve) => setTimeout(resolve, 800));
}

const browser = await chromium.launch(gpu
  ? { headless: false, executablePath: process.env.CHROME_PATH ?? '/usr/bin/google-chrome', env: { ...process.env, GALLIUM_DRIVER: 'd3d12' } }
  : { headless: true, executablePath: process.env.CHROME_PATH ?? '/usr/bin/google-chrome', args: ['--no-sandbox'] });
try {
  const context = await browser.newContext({ viewport: { width: 1672, height: 941 }, deviceScaleFactor: 1.5, reducedMotion: 'no-preference' });
  const page = await context.newPage();
  // keep the drawing buffer readable so the finished frame can be exported with its transparency
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (type, attributes) {
      if (String(type).includes('webgl')) return original.call(this, type, { ...(attributes ?? {}), preserveDrawingBuffer: true });
      return original.call(this, type, attributes);
    };
  });
  await page.goto(`${base}/?visualTest=1`);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForSelector('[data-webgl="live"]', { timeout: 120_000 });
  await page.waitForTimeout(2500);
  const { dataUrl, width, height } = await page.evaluate(() => {
    const canvas = document.querySelector('.phone-canvas canvas');
    return { dataUrl: canvas.toDataURL('image/webp', 0.92), width: canvas.width, height: canvas.height };
  });
  const webp = Buffer.from(dataUrl.split(',')[1], 'base64');
  await fs.writeFile(path.join(root, 'public/app-screens/phone-poster.webp'), webp);
  console.log(`poster written: ${width}x${height}, ${webp.length} bytes`);
} finally {
  await browser.close();
  server?.kill();
}
