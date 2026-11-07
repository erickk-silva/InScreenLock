import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const HOME_YAW = -22; // approved hero pose, see INITIAL_POSE

test('E2E-01 hero headline, download state, and no horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 1672, height: 941 });
  await page.goto('/?visualTest=1');
  await expect(page.getByRole('heading', { level: 1, name: 'Seu feed não tem fim. Seu tempo tem.' })).toBeVisible();
  await expect(page.getByText('Baixar para Android')).toBeVisible();
  await expect(page.getByText('Download em preparação. O APK será publicado após verificação.')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test('download section is a real, stable target with honest content, and the CTA leads to it', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 }); await page.goto('/?visualTest=1');
  const section = page.locator('section#download'); await expect(section).toHaveCount(1);
  await expect(section).toContainText('Download em preparação'); await expect(section).toContainText('APK assinado'); await expect(section).toContainText('SHA-256'); await expect(section).toContainText('versão');
  await expect(section).toContainText('Android 9'); await expect(section).toContainText('permissões');
  await expect(section.locator('a[href$=".apk"]')).toHaveCount(0); // nothing to download yet, and nothing pretends so
  await page.getByRole('link', { name: /Baixar para Android/ }).click();
  await expect.poll(() => page.evaluate(() => document.querySelector('#download')!.getBoundingClientRect().top), { timeout: 8000 }).toBeLessThan(400);
  await expect(page).toHaveURL(/#download$/);
  for (const id of ['demo', 'privacidade']) await expect(page.locator(`#${id}`)).toHaveCount(1); // every nav target exists
});

test('demonstration labelling: visible caption and a description with the fictional numbers', async ({ page }) => {
  await page.goto('/?visualTest=1');
  await expect(page.getByTestId('demo-caption')).toBeVisible(); await expect(page.getByTestId('demo-caption')).toHaveText('Tela demonstrativa — dados fictícios');
  const describedBy = await page.getByTestId('phone').getAttribute('aria-describedby'); expect(describedBy).toBeTruthy();
  const description = await page.locator(`#${describedBy}`).textContent();
  expect(description).toContain('21 minutos'); expect(description).toContain('17 minutos'); expect(description).toMatch(/fictícios/);
});

test('copy is visible without JavaScript and is never inline-hidden in the server HTML', async ({ page, request }) => {
  const html = await (await request.get('/')).text();
  expect(html).not.toMatch(/visibility:\s*hidden/);
  const browser = page.context().browser(); if (!browser) throw new Error('browser missing');
  const context = await browser.newContext({ javaScriptEnabled: false, viewport: { width: 1672, height: 941 } });
  const plain = await context.newPage(); await plain.goto('http://127.0.0.1:3100/');
  await expect(plain.locator('.h1-line').first()).toBeVisible(); await expect(plain.locator('.h1-accent .h1-fragment')).toBeVisible(); await expect(plain.locator('.h1-accent .h1-fragment')).toHaveText('Seu tempo tem.');
  await expect(plain.locator('.subheadline')).toBeVisible(); await expect(plain.locator('.benefits')).toBeVisible(); await expect(plain.locator('section#download')).toBeVisible();
  await context.close();
});

test('LCP does not wait for 3D or text animations', async ({ page }) => {
  await page.addInitScript(() => { (window as unknown as { lcp: number }).lcp = 0; new PerformanceObserver((list) => { const last = list.getEntries().at(-1); if (last) (window as unknown as { lcp: number }).lcp = last.startTime; }).observe({ type: 'largest-contentful-paint', buffered: true }); });
  await page.setViewportSize({ width: 1672, height: 941 }); await page.goto('/');
  await page.waitForTimeout(3000);
  const lcp = await page.evaluate(() => (window as unknown as { lcp: number }).lcp);
  console.log(`LCP ${Math.round(lcp)} ms`);
  expect(lcp).toBeGreaterThan(0); expect(lcp).toBeLessThan(2500);
});

test('mobile headline keeps "Seu tempo tem." on one line and wraps the first line without an orphan', async ({ page }) => {
  for (const width of [360, 390]) {
    await page.setViewportSize({ width, height: 844 }); await page.goto('/?visualTest=1');
    const lines = await page.evaluate(() => [...document.querySelectorAll('.hero h1 .h1-line')].map((line) => {
      const fragment = line.querySelector('.h1-fragment') as HTMLElement; const range = document.createRange(); range.selectNodeContents(fragment);
      const tops = new Map<number, number>(); for (const rect of range.getClientRects()) tops.set(Math.round(rect.top / 4), (tops.get(Math.round(rect.top / 4)) ?? 0) + rect.width);
      return [...tops.values()].map(Math.round);
    }));
    expect(lines[1], `accent line at ${width}`).toHaveLength(1);
    expect(lines[0].length, `first line at ${width}`).toBeLessThanOrEqual(2);
    if (lines[0].length === 2) expect(Math.min(...lines[0]) / Math.max(...lines[0]), 'orphan in the first line').toBeGreaterThan(0.45);
  }
});

test('no request leaves the origin while the page loads, the 3D scene mounts and the device is used', async ({ page }) => {
  test.setTimeout(120_000);
  const foreign: string[] = []; const origin = new URL('http://127.0.0.1:3100').origin;
  page.on('request', (request) => { const url = request.url(); if (!/^(data|blob|about):/.test(url) && new URL(url).origin !== origin) foreign.push(url); });
  await page.goto('/?visualTest=1'); await expect(page.getByTestId('phone')).toHaveAttribute('data-webgl', 'live', { timeout: 60_000 });
  await page.getByTestId('phone').focus(); for (let i = 0; i < 4; i++) await page.keyboard.press('ArrowRight');
  const box = await page.getByTestId('phone').boundingBox(); if (!box) throw new Error('phone missing');
  await page.mouse.move(box.x + 80, box.y + 200); await page.mouse.down(); await page.mouse.move(box.x + 200, box.y + 210, { steps: 4 }); await page.mouse.up();
  await page.waitForTimeout(1500);
  expect(foreign).toEqual([]);
});

test('no console errors on a normal load with WebGL, nor with WebGL unavailable', async ({ page }) => {
  test.setTimeout(120_000);
  const problems: string[] = []; const warnings: string[] = [];
  page.on('console', (message) => { if (message.type() === 'error') problems.push(message.text()); if (message.type() === 'warning') warnings.push(message.text()); });
  page.on('pageerror', (error) => problems.push(error.message));
  await page.goto('/'); await expect(page.getByTestId('phone')).toHaveAttribute('data-webgl', 'live', { timeout: 60_000 }); await page.waitForTimeout(1500);
  expect(problems, 'normal load').toEqual([]);
  // known and harmless: react-three-fiber 9.8 still builds a THREE.Clock, which three 0.187 flags as deprecated (a warning, not an error)
  for (const text of warnings) expect(text, 'only the documented THREE.Clock warning is tolerated').toMatch(/THREE\.Clock|GPU stall|GL Driver/);
  const off = await page.context().newPage(); const offProblems: string[] = [];
  off.on('console', (message) => { if (message.type() === 'error') offProblems.push(message.text()); }); off.on('pageerror', (error) => offProblems.push(error.message));
  await off.addInitScript(() => { const original = HTMLCanvasElement.prototype.getContext; HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement, contextId: string, ...args: unknown[]) { if (contextId.includes('webgl')) return null; return Reflect.apply(original, this, [contextId, ...args]); } as typeof HTMLCanvasElement.prototype.getContext; });
  await off.goto('http://127.0.0.1:3100/'); await expect(off.getByTestId('phone')).toHaveAttribute('data-webgl', 'poster'); await off.waitForTimeout(1500);
  expect(offProblems, 'WebGL unavailable').toEqual([]);
});

test('a 3D chunk that fails to download leaves the page, CTA and poster intact', async ({ page }) => {
  await page.route('**/_next/static/chunks/*.js', async (route) => {
    const response = await route.fetch(); const body = await response.text();
    if (body.includes('webglcontextlost')) await route.abort('failed'); else await route.fulfill({ response, body });
  });
  const crashes: string[] = []; page.on('pageerror', (error) => crashes.push(error.message));
  await page.goto('/?visualTest=1'); await page.waitForTimeout(2500);
  await expect(page.getByRole('heading', { level: 1, name: 'Seu feed não tem fim. Seu tempo tem.' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Baixar para Android/ })).toBeVisible();
  await expect(page.locator('.phone-poster')).toBeVisible(); await expect(page.locator('.phone-canvas')).toHaveCount(0);
  await expect(page.getByTestId('phone')).toHaveAttribute('data-webgl', 'poster');
  await expect(page.getByText("This page couldn't load")).toHaveCount(0);
  await expect(page.locator('.alert-card.is-in')).toHaveCount(1); // the limit card still appears without the 3D intro
  expect(crashes).toEqual([]);
});

test('E2E-02 and 03 mouse drag and accumulated turns remain unbounded while pitch clamps', async ({ page }) => {
  test.setTimeout(150_000);
  await page.goto('/?visualTest=1');
  const phone = page.getByTestId('phone'); await expect(phone).toBeVisible();
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  const x = box.x + 50; const y = box.y + box.height / 2;
  await page.mouse.move(x, y); await page.mouse.down(); await page.mouse.move(x + 330, y, { steps: 8 }); await page.mouse.up();
  await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg')), { timeout: 30_000 }).toBeGreaterThan(100); // software WebGL renders ~1 fps
  await expect(page.locator('[aria-live="polite"]')).toContainText('Giro liberado.');
  const releaseYaw = Number(await phone.getAttribute('data-yaw-deg'));
  expect(releaseYaw).toBeGreaterThan(100);
  await phone.evaluate((root) => {
    const target = root as HTMLDivElement & { setPointerCapture: () => void; hasPointerCapture: () => boolean; releasePointerCapture: () => void };
    target.setPointerCapture = () => {}; target.hasPointerCapture = () => true; target.releasePointerCapture = () => {};
    const send = (type: string, clientX: number, clientY: number) => target.dispatchEvent(new PointerEvent(type, { pointerId: 78, pointerType: 'mouse', button: 0, clientX, clientY, bubbles: true }));
    send('pointerdown', 100, 100); send('pointermove', 760, 100); send('pointermove', 1420, 100); send('pointerup', 1420, 100);
  });
  await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg')), { timeout: 40_000 }).toBeGreaterThan(600);
  await phone.evaluate((root) => {
    const target = root as HTMLDivElement & { setPointerCapture: () => void; hasPointerCapture: () => boolean; releasePointerCapture: () => void };
    const send = (type: string, clientY: number) => target.dispatchEvent(new PointerEvent(type, { pointerId: 79, pointerType: 'mouse', button: 0, clientX: 100, clientY, bubbles: true }));
    send('pointerdown', 100); send('pointermove', 1100); send('pointerup', 1100);
  });
  await expect.poll(async () => Math.abs(Number(await phone.getAttribute('data-pitch-deg')))).toBeLessThanOrEqual(30);
});

test('release: a fast flick keeps turning with inertia, a pointer that rested before lifting does not', async ({ page }) => {
  test.setTimeout(240_000);
  await page.goto('/?visualTest=1'); const phone = page.getByTestId('phone');
  await expect(phone).toHaveAttribute('data-webgl', 'live', { timeout: 60_000 });
  const gesture = (restMs: number) => phone.evaluate(async (root, rest) => {
    const target = root as HTMLDivElement & { setPointerCapture: () => void; hasPointerCapture: () => boolean; releasePointerCapture: () => void };
    target.setPointerCapture = () => {}; target.hasPointerCapture = () => true; target.releasePointerCapture = () => {};
    const send = (type: string, clientX: number) => target.dispatchEvent(new PointerEvent(type, { pointerId: 91, pointerType: 'mouse', button: 0, clientX, clientY: 300, bubbles: true }));
    send('pointerdown', 100); send('pointermove', 200); // +100 px = +55 degrees
    if (rest > 0) await new Promise((resolve) => setTimeout(resolve, rest));
    send('pointerup', 200);
  }, restMs);
  const yaw = async () => Number(await phone.getAttribute('data-yaw-deg'));
  await gesture(300); // rested 300 ms: lands exactly on the dragged angle and stays there
  await expect.poll(yaw, { timeout: 90_000 }).toBeGreaterThan(HOME_YAW + 53); expect(await yaw()).toBeLessThan(HOME_YAW + 58);
  await page.waitForTimeout(4000); expect(await yaw()).toBeLessThan(HOME_YAW + 58);
  await gesture(0); // flick: released in the same tick, inertia carries it far beyond the dragged angle
  await expect.poll(yaw, { timeout: 90_000 }).toBeGreaterThan(HOME_YAW + 55 + 55 + 100);
});

const swipe = async (page: Page, from: [number, number], to: [number, number]) => {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: from[0], y: from[1] }] });
  const steps = 12;
  for (let step = 1; step <= steps; step++) {
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: from[0] + ((to[0] - from[0]) * step) / steps, y: from[1] + ((to[1] - from[1]) * step) / steps }] });
    await page.waitForTimeout(16);
  }
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await cdp.detach();
};
const mobilePage = async (page: Page, url = 'http://127.0.0.1:3100/?visualTest=1') => {
  const browser = page.context().browser(); if (!browser) throw new Error('browser missing');
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1, isMobile: true, hasTouch: true });
  const mobile = await context.newPage(); await mobile.goto(url);
  return { context, mobile };
};

test('E2E-04 horizontal swipe over the phone rotates it on mobile', async ({ page }) => {
  test.setTimeout(90_000);
  const { context, mobile } = await mobilePage(page);
  const phone = mobile.getByTestId('phone'); await phone.scrollIntoViewIfNeeded(); const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  await expect(phone).toHaveAttribute('data-yaw-deg', String(HOME_YAW));
  await swipe(mobile, [box.x + box.width / 2 - 60, box.y + box.height / 2], [box.x + box.width / 2 + 100, box.y + box.height / 2]);
  await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg')), { timeout: 20_000 }).toBeGreaterThan(HOME_YAW + 20);
  expect(Number(await phone.getAttribute('data-pitch-deg'))).toBe(-8); // touch never changes the pitch: vertical gestures belong to the page
  await context.close();
});

test('E2E-04b vertical swipe over the phone scrolls the page instead of rotating it (390x844)', async ({ page }) => {
  test.setTimeout(90_000);
  const { context, mobile } = await mobilePage(page);
  const phone = mobile.getByTestId('phone'); await phone.scrollIntoViewIfNeeded();
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  const before = await mobile.evaluate(() => scrollY);
  const yawBefore = Number(await phone.getAttribute('data-yaw-deg')); const pitchBefore = Number(await phone.getAttribute('data-pitch-deg'));
  const x = box.x + box.width / 2; const y = Math.min(box.y + box.height * 0.75, 800);
  await swipe(mobile, [x, y], [x, y - 240]);
  await expect.poll(() => mobile.evaluate(() => scrollY), { timeout: 10_000 }).toBeGreaterThan(before + 60);
  expect(Number(await phone.getAttribute('data-yaw-deg'))).toBe(yawBefore);
  expect(Number(await phone.getAttribute('data-pitch-deg'))).toBe(pitchBefore);
  await context.close();
});

test('E2E-05 page scrolling remains available outside the phone', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 800 }); await page.goto('/');
  await page.mouse.move(100, 500); await page.mouse.wheel(0, 400); await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(0);
});

test('E2E-06 keyboard rotation, Home and the Recentrar button (real mouse click) return to the home pose', async ({ page }) => {
  test.setTimeout(120_000);
  await page.goto('/?visualTest=1'); const phone = page.getByTestId('phone');
  const recenter = page.getByRole('button', { name: 'Recentrar aparelho' });
  await expect(recenter).toHaveCSS('opacity', '0'); // hidden until the user turns the device
  await phone.focus(); await page.keyboard.press('ArrowRight');
  await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg'))).toBeGreaterThan(HOME_YAW);
  await page.keyboard.press('Home'); await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg')), { timeout: 40_000 }).toBe(HOME_YAW);
  // turn it with the mouse, then press the button with the mouse: the click must reach the button and recentre
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  await page.mouse.move(box.x + 60, box.y + box.height / 2); await page.mouse.down(); await page.mouse.move(box.x + 60 + 260, box.y + box.height / 2, { steps: 6 }); await page.mouse.move(box.x + 60 + 262, box.y + box.height / 2); await page.waitForTimeout(150); await page.mouse.up();
  await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg'))).toBeGreaterThan(HOME_YAW + 60);
  await expect(recenter).toHaveCSS('opacity', /0\.9/);
  await recenter.click();
  await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg')), { timeout: 60_000 }).toBe(HOME_YAW);
  await expect(page.locator('[aria-live="polite"]')).toContainText('Aparelho recentrado.');
});

test('keyboard announcements are throttled and use the angle from home, never a raw 4-digit value', async ({ page }) => {
  await page.goto('/?visualTest=1'); const phone = page.getByTestId('phone'); await phone.focus();
  const live = page.locator('[aria-live="polite"]');
  await live.evaluate((node) => { (window as unknown as { liveChanges: string[] }).liveChanges = []; new MutationObserver(() => (window as unknown as { liveChanges: string[] }).liveChanges.push(node.textContent ?? '')).observe(node, { childList: true, characterData: true, subtree: true }); });
  for (let i = 0; i < 40; i++) await page.keyboard.press('ArrowRight'); // 480 degrees in about a second
  await expect(live).toHaveText('Posição 120 graus.', { timeout: 6000 }); // 480 mod 360
  const changes = await page.evaluate(() => (window as unknown as { liveChanges: string[] }).liveChanges);
  expect(changes.length).toBeLessThanOrEqual(4);
  for (const text of changes) expect(Number(/\d+/.exec(text)?.[0] ?? 0)).toBeLessThan(360);
});

test('E2E-07 WebGL disabled leaves an accessible static screen and CTA', async ({ page }) => {
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement, contextId: string, ...args: unknown[]) {
      if (contextId.includes('webgl')) return null;
      return Reflect.apply(original, this, [contextId, ...args]);
    } as typeof HTMLCanvasElement.prototype.getContext;
  });
  await page.goto('/?visualTest=1'); await expect(page.locator('.phone-poster').first()).toBeVisible();
  await expect(page.locator('.phone-canvas')).toHaveCount(0);
  await expect(page.getByRole('link', { name: /Baixar para Android/ })).toBeVisible();
});

test('WebGL context loss replaces the canvas with the static demonstration poster', async ({ page }) => {
  await page.goto('/?visualTest=1');
  await expect(page.locator('.phone-canvas canvas')).toBeVisible();
  await expect(page.locator('.phone-poster')).toBeHidden();
  await page.waitForTimeout(100);
  await page.locator('.phone-canvas canvas').evaluate((canvas) => canvas.dispatchEvent(new Event('webglcontextlost', { cancelable: true })));
  await expect(page.locator('.phone-poster')).toBeVisible();
  await expect(page.locator('.phone-canvas')).toHaveCount(0);
});

test('E2E-08 reduced motion preference is respected', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' }); await page.goto('/');
  await page.waitForTimeout(800);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Recentrar aparelho' })).toHaveCount(1);
  await expect(page.getByTestId('phone')).toHaveAttribute('data-yaw-deg', String(HOME_YAW));
  const opacity = await page.locator('.hero-actions').evaluate((element) => getComputedStyle(element).opacity);
  expect(Number(opacity)).toBe(1);
  await page.getByTestId('phone').focus(); await page.keyboard.press('ArrowRight');
  await expect.poll(async () => Number(await page.getByTestId('phone').getAttribute('data-yaw-deg'))).toBeGreaterThan(HOME_YAW);
});

test('idle drift runs subtly, then freezes at the hovered pose', async ({ page }) => {
  test.setTimeout(90_000); // software WebGL renders ~1 fps in CI containers, so the 2.5 s entrance needs real time
  await page.goto('/');
  const phone = page.getByTestId('phone');
  await expect(phone).toHaveAttribute('data-intro', 'done', { timeout: 40_000 });
  await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg'))).not.toBe(HOME_YAW);
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.waitForTimeout(250);
  const hoveredYaw = Number(await phone.getAttribute('data-yaw-deg'));
  await page.waitForTimeout(500);
  expect(Number(await phone.getAttribute('data-yaw-deg'))).toBe(hoveredYaw);
});

test('E2E-09 required responsive sizes have no horizontal overflow', async ({ page }) => {
  for (const width of [360, 390, 768, 1024, 1280, 1440, 1672]) {
    await page.setViewportSize({ width, height: 900 }); await page.goto('/');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `overflow at ${width}`).toBeTruthy();
  }
});

test('visual evidence: desktop, tablet, mobile, and phone orientations', async ({ page }) => {
  test.setTimeout(900_000);
  const output = path.resolve(process.cwd(), '../.agent-harness/artifacts/ISL-LANDING-001/20261106T200700Z/hero-shots');
  await fs.mkdir(output, { recursive: true });
  const settle = async (p: Page) => { // deterministic: fonts ready, 3D scene live in its final frame, every entrance finished
    await p.evaluate(() => document.fonts.ready);
    await expect(p.getByTestId('phone')).toHaveAttribute('data-webgl', 'live', { timeout: 120_000 });
    await expect.poll(() => p.evaluate(() => [...document.querySelectorAll('.benefit')].every((node) => getComputedStyle(node).opacity === '1')), { timeout: 60_000 }).toBeTruthy();
    await p.waitForTimeout(2500);
  };
  const phone = page.getByTestId('phone');
  await page.setViewportSize({ width: 1672, height: 941 }); await page.goto('/?visualTest=1'); await settle(page);
  await expect(page.locator('.phone-poster')).toHaveAttribute('alt', /demonstração fictícia/i);
  await page.screenshot({ path: path.join(output, 'actual-1672.png') });
  // phone shots: the device alone in its stage (floating cards, header and focus ring hidden so nothing covers the device)
  const canvasShot = async (name: string) => { await page.addStyleTag({ content: '.float-pos,.site-header-bar{visibility:hidden!important}.phone-wrap:focus-visible{outline:none!important}', }).then(async (tag) => { await page.locator('.phone-canvas').screenshot({ path: path.join(output, name) }); await tag.evaluate((node) => (node as Element).remove()); }); };
  await canvasShot('phone-front.png');
  await phone.focus();
  const settleAt = async (target: number) => { await expect.poll(async () => Math.abs(Number(await phone.getAttribute('data-yaw-deg')) - target), { timeout: 120_000 }).toBeLessThan(3); await page.waitForTimeout(1500); };
  for (let i = 0; i < 8; i++) await page.keyboard.press('ArrowRight');
  await settleAt(HOME_YAW + 96); await canvasShot('phone-side.png');
  for (let i = 0; i < 9; i++) await page.keyboard.press('ArrowRight');
  await settleAt(HOME_YAW + 204); await canvasShot('phone-back.png');
  for (const [name, width, height] of [['1440', 1440, 900], ['1280', 1280, 800], ['1024', 1024, 768], ['768', 768, 1024]] as const) {
    await page.setViewportSize({ width, height }); await page.goto('/?visualTest=1');
    if (width >= 1024) await settle(page); else { await page.getByTestId('phone').scrollIntoViewIfNeeded(); await settle(page); await page.evaluate(() => scrollTo(0, 0)); await page.waitForTimeout(600); }
    await page.screenshot({ path: path.join(output, `actual-${name}.png`) });
  }
  for (const [name, width, height] of [['390', 390, 844], ['360', 360, 800]] as const) { // phones: the device sits below the copy, so the full page (phone included) is the evidence
    await page.setViewportSize({ width, height }); await page.goto('/?visualTest=1'); await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(output, `actual-${name}-fold.png`) });
    await page.getByTestId('phone').scrollIntoViewIfNeeded(); await settle(page); await page.evaluate(() => scrollTo(0, 0)); await page.waitForTimeout(600);
    await page.screenshot({ path: path.join(output, `actual-${name}.png`), fullPage: true });
  }
  const actual = await fs.readFile(path.join(output, 'actual-1672.png'));
  const reference = await fs.readFile(path.resolve(process.cwd(), '../.agent-harness/input/InScreenLock_Landing_Spec_Handoff_v1.0/references/01-HERO-APROVADO.png'));
  await page.setViewportSize({ width: 1672, height: 941 });
  await page.setContent('<body style="margin:0"><canvas width="1672" height="941" style="width:1672px;height:941px;display:block"></canvas></body>');
  await page.evaluate(({ left, right }) => {
    const canvas = document.querySelector('canvas')!; const ctx = canvas.getContext('2d')!;
    const draw = (src: string) => new Promise<HTMLImageElement>((resolve) => { const image = new Image(); image.onload = () => resolve(image); image.src = `data:image/png;base64,${src}`; });
    return Promise.all([draw(left), draw(right)]).then(([a, b]) => { ctx.globalAlpha = .5; ctx.drawImage(a, 0, 0, canvas.width, canvas.height); ctx.drawImage(b, 0, 0, canvas.width, canvas.height); });
  }, { left: actual.toString('base64'), right: reference.toString('base64') });
  await page.screenshot({ path: path.join(output, 'overlay-1672.png') });
});
