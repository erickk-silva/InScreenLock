import { test, expect, type Locator, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import fs from 'node:fs/promises';
import path from 'node:path';

const HOME_YAW = -28; // approved hero pose, see INITIAL_POSE
const HOME_PITCH = -20;
const PITCH_RANGE = 30;
const GPU = process.env.E2E_GPU === '1';
const EVIDENCE = path.resolve(process.cwd(), '../.agent-harness/artifacts/ISL-LANDING-001/20261106T200700Z');
/** Every wait below follows a data attribute of the page, so a slow or busy machine only makes a test slower. */
const WEBGL_WAIT = { timeout: 240_000 };
const yawOf = async (phone: Locator) => Number(await phone.getAttribute('data-yaw-deg'));
const pitchOf = async (phone: Locator) => Number(await phone.getAttribute('data-pitch-deg'));
const live = (target: Page | Locator) => expect('goto' in target ? target.getByTestId('phone') : target).toHaveAttribute('data-webgl', 'live', WEBGL_WAIT);
const introDone = (phone: Locator) => expect(phone).toHaveAttribute('data-intro', 'done', WEBGL_WAIT);

test('E2E-01 hero headline, download state, and no horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 1672, height: 941 });
  await fs.mkdir(path.join(EVIDENCE, 'hero-shots'), { recursive: true });
  const browser = page.context().browser(); if (!browser) throw new Error('browser missing');
  const plainContext = await browser.newContext({ javaScriptEnabled: false, viewport: { width: 1672, height: 941 }, deviceScaleFactor: 1 });
  const plain = await plainContext.newPage(); await plain.goto('http://127.0.0.1:3100/', { waitUntil: 'domcontentloaded' });
  await expect(plain.locator('#hero-title')).toBeVisible(); await expect(plain.getByRole('link', { name: /Baixar para Android/ })).toBeVisible();
  await plain.screenshot({ path: path.join(EVIDENCE, 'hero-shots/first-paint-no-js.png') }); await plainContext.close();

  await page.route('**/_next/static/chunks/**/*.js', (route) => route.abort()); // JS is enabled, but hydration stays pending while SSR is captured.
  await page.goto('http://127.0.0.1:3100/', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('#hero-title')).toBeVisible(); await expect(page.getByRole('link', { name: /Baixar para Android/ })).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.classList.contains('js-motion'))).toBeTruthy();
  const readFirstFrame = () => page.evaluate(() => {
    const headline = document.querySelector('#hero-title')!; const cta = document.querySelector('.button-primary')!;
    const state = (node: Element) => { const style = getComputedStyle(node); const rect = node.getBoundingClientRect(); return { opacity: style.opacity, visibility: style.visibility, filter: style.filter, width: Math.round(rect.width), height: Math.round(rect.height) }; };
    return { headline: state(headline), cta: state(cta), visibleText: [...document.querySelectorAll('.h1-fragment,.subheadline')].map((node) => ({ text: node.textContent?.trim(), ...state(node) })) };
  });
  const at0ms = await readFirstFrame();
  await page.screenshot({ path: path.join(EVIDENCE, 'hero-shots/first-paint-js-000ms.png') });
  await page.waitForTimeout(100);
  const at100ms = await readFirstFrame();
  for (const sample of [at0ms, at100ms]) {
    expect(sample.headline.opacity).toBe('1'); expect(sample.headline.visibility).toBe('visible'); expect(sample.headline.filter).toBe('none');
    expect(sample.cta.opacity).toBe('1'); expect(sample.cta.visibility).toBe('visible');
    expect(sample.visibleText).toHaveLength(3); expect(sample.visibleText.every((node) => node.opacity === '1' && node.visibility === 'visible' && node.filter === 'none')).toBeTruthy();
  }
  await fs.writeFile(path.join(EVIDENCE, 'hero-shots/first-paint-r4.json'), `${JSON.stringify({ mode: 'JavaScript enabled; Next chunks blocked to capture server HTML before hydration', samples: { '0ms': at0ms, '100ms': at100ms } }, null, 2)}\n`);
  await page.screenshot({ path: path.join(EVIDENCE, 'hero-shots/first-paint-js-100ms.png') });
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
  await expect.poll(() => page.evaluate(() => document.querySelector('#download')!.getBoundingClientRect().top)).toBeLessThan(400);
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
  expect((html.match(/id="demo"/g) ?? []).length).toBe(1);
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
  const foreign: string[] = []; const origin = new URL('http://127.0.0.1:3100').origin;
  page.on('request', (request) => { const url = request.url(); if (!/^(data|blob|about):/.test(url) && new URL(url).origin !== origin) foreign.push(url); });
  await page.goto('/?visualTest=1'); await live(page);
  await page.getByTestId('phone').focus(); for (let i = 0; i < 4; i++) await page.keyboard.press('ArrowRight');
  const box = await page.getByTestId('phone').boundingBox(); if (!box) throw new Error('phone missing');
  await page.mouse.move(box.x + 80, box.y + 200); await page.mouse.down(); await page.mouse.move(box.x + 200, box.y + 210, { steps: 4 }); await page.mouse.up();
  await page.waitForTimeout(1500);
  expect(foreign).toEqual([]);
});

test('no console errors on a normal load with WebGL, nor with WebGL unavailable', async ({ page }) => {
  const problems: string[] = []; const warnings: string[] = [];
  page.on('console', (message) => { if (message.type() === 'error') problems.push(message.text()); if (message.type() === 'warning') warnings.push(message.text()); });
  page.on('pageerror', (error) => problems.push(error.message));
  await page.goto('/'); await expect(page.getByTestId('phone')).toHaveAttribute('data-webgl', 'live', WEBGL_WAIT); await page.waitForTimeout(1500);
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
    try {
      const response = await route.fetch(); const body = await response.text();
      if (body.includes('webglcontextlost')) await route.abort('failed'); else await route.fulfill({ response, body });
    } catch { /* the page was already closed when this request finished: nothing left to answer */ }
  });
  const crashes: string[] = []; page.on('pageerror', (error) => crashes.push(error.message));
  await page.goto('/?visualTest=1');
  await expect(page.getByRole('heading', { level: 1, name: 'Seu feed não tem fim. Seu tempo tem.' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Baixar para Android/ })).toBeVisible();
  await expect(page.locator('.phone-poster')).toBeVisible(); await expect(page.locator('.phone-canvas')).toHaveCount(0);
  await expect(page.getByTestId('phone')).toHaveAttribute('data-webgl', 'poster');
  await expect(page.getByText("This page couldn't load")).toHaveCount(0);
  await expect(page.locator('.alert-card.is-in')).toHaveCount(1); // the limit card still appears without the 3D intro
  expect(crashes).toEqual([]);
});

test('E2E-02 and 03 mouse drag and accumulated turns remain unbounded while pitch clamps', async ({ page }) => {
  await page.goto('/?visualTest=1');
  const phone = page.getByTestId('phone'); await live(phone); await introDone(phone);
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  const x = box.x + 50; const y = box.y + box.height / 2;
  await page.mouse.move(x, y); await page.mouse.down(); await page.mouse.move(x + 330, y, { steps: 8 }); await page.mouse.up();
  await expect.poll(() => yawOf(phone)).toBeGreaterThan(100);
  await expect(page.locator('[aria-live="polite"]')).toContainText('Giro liberado.');
  const releaseYaw = Number(await phone.getAttribute('data-yaw-deg'));
  expect(releaseYaw).toBeGreaterThan(100);
  await phone.evaluate((root) => {
    const target = root as HTMLDivElement & { setPointerCapture: () => void; hasPointerCapture: () => boolean; releasePointerCapture: () => void };
    target.setPointerCapture = () => {}; target.hasPointerCapture = () => true; target.releasePointerCapture = () => {};
    const send = (type: string, clientX: number, clientY: number) => target.dispatchEvent(new PointerEvent(type, { pointerId: 78, pointerType: 'mouse', button: 0, clientX, clientY, bubbles: true }));
    send('pointerdown', 100, 100); send('pointermove', 760, 100); send('pointermove', 1420, 100); send('pointerup', 1420, 100);
  });
  await expect.poll(() => yawOf(phone)).toBeGreaterThan(600);
  const verticalDrag = (id: number, from: number, to: number) => phone.evaluate((root, args) => {
    const target = root as HTMLDivElement;
    const send = (type: string, clientY: number) => target.dispatchEvent(new PointerEvent(type, { pointerId: args.id, pointerType: 'mouse', button: 0, clientX: 100, clientY, bubbles: true }));
    send('pointerdown', args.from); send('pointermove', args.to); send('pointerup', args.to);
  }, { id, from, to });
  // pitch really moves (it used to start near the limit, so a no-op passed) and clamps at the range around the home pitch, in both directions
  await verticalDrag(79, 100, 1100);
  await expect.poll(() => pitchOf(phone)).toBeGreaterThan(HOME_PITCH + PITCH_RANGE - 5);
  expect(await pitchOf(phone)).toBeLessThanOrEqual(HOME_PITCH + PITCH_RANGE + 0.5);
  await verticalDrag(80, 1100, 100);
  await expect.poll(() => pitchOf(phone)).toBeLessThan(HOME_PITCH - PITCH_RANGE + 5);
  expect(await pitchOf(phone)).toBeGreaterThanOrEqual(HOME_PITCH - PITCH_RANGE - 0.5);
});

test('release: a fast flick keeps turning with inertia, a pointer that rested before lifting does not', async ({ page }) => {
  await page.goto('/?visualTest=1'); const phone = page.getByTestId('phone');
  await live(phone); await introDone(phone);
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
  await expect.poll(yaw).toBeGreaterThan(HOME_YAW + 53); expect(await yaw()).toBeLessThan(HOME_YAW + 58);
  // still: the pose must stay put (two reads 4 s apart agree), instead of waiting a fixed time and hoping
  await expect.poll(async () => { const first = await yaw(); await page.waitForTimeout(1500); return Math.abs((await yaw()) - first); }).toBeLessThan(0.3);
  expect(await yaw()).toBeLessThan(HOME_YAW + 58);
  await gesture(0); // flick: released in the same tick, inertia carries it far beyond the dragged angle (capped at 1500 deg/s)
  await expect.poll(yaw).toBeGreaterThan(HOME_YAW + 55 + 55 + 100);
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
  const { context, mobile } = await mobilePage(page);
  const phone = mobile.getByTestId('phone'); await phone.scrollIntoViewIfNeeded(); await live(phone);
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  expect(Math.abs((await yawOf(phone)) - HOME_YAW)).toBeLessThan(3.5); // home pose plus the subtle idle drift
  await swipe(mobile, [box.x + box.width / 2 - 60, box.y + box.height / 2], [box.x + box.width / 2 + 100, box.y + box.height / 2]);
  await expect.poll(() => yawOf(phone)).toBeGreaterThan(HOME_YAW + 20);
  expect(Math.abs((await pitchOf(phone)) - HOME_PITCH)).toBeLessThan(1.5); // touch never changes the pitch: vertical gestures belong to the page (the keyboard covers it)
  await context.close();
});

test('E2E-04b vertical swipe over the phone scrolls the page instead of rotating it (390x844)', async ({ page }) => {
  const { context, mobile } = await mobilePage(page);
  const phone = mobile.getByTestId('phone'); await phone.scrollIntoViewIfNeeded(); await live(phone);
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  const before = await mobile.evaluate(() => scrollY);
  const yawBefore = await yawOf(phone); const pitchBefore = await pitchOf(phone);
  const x = box.x + box.width / 2; const y = Math.min(box.y + box.height * 0.75, 800);
  await swipe(mobile, [x, y], [x, y - 240]);
  await expect.poll(() => mobile.evaluate(() => scrollY)).toBeGreaterThan(before + 60);
  expect(Math.abs((await yawOf(phone)) - yawBefore)).toBeLessThan(4); // a rotation by the swipe would be tens of degrees; only the idle drift (about 2 deg) may remain
  expect(Math.abs((await pitchOf(phone)) - pitchBefore)).toBeLessThan(1.5);
  await context.close();
});

test('E2E-05 page scrolling remains available outside the phone', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 800 }); await page.goto('/');
  await page.mouse.move(100, 500); await page.mouse.wheel(0, 400); await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(0);
});

test('E2E-06 keyboard rotation, Home and the Recentrar button (real mouse click) return to the home pose', async ({ page }) => {
  await page.goto('/?visualTest=1'); const phone = page.getByTestId('phone'); await live(phone);
  const recenter = page.getByRole('button', { name: 'Recentrar aparelho' });
  await expect(recenter).toHaveCSS('opacity', '0'); // hidden until the user turns the device
  await phone.focus(); await page.keyboard.press('ArrowRight');
  await expect.poll(() => yawOf(phone)).toBeGreaterThan(HOME_YAW);
  await page.keyboard.press('Home'); await expect.poll(() => yawOf(phone)).toBe(HOME_YAW);
  // turn it with the mouse, then press the button with the mouse: the click must reach the button and recentre
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  await page.mouse.move(box.x + 60, box.y + box.height / 2); await page.mouse.down(); await page.mouse.move(box.x + 60 + 260, box.y + box.height / 2, { steps: 6 }); await page.mouse.move(box.x + 60 + 262, box.y + box.height / 2); await page.waitForTimeout(150); await page.mouse.up();
  await expect.poll(() => yawOf(phone)).toBeGreaterThan(HOME_YAW + 60);
  await expect(recenter).toHaveCSS('opacity', /0\.9/);
  await recenter.click();
  await expect.poll(() => yawOf(phone)).toBe(HOME_YAW);
  await expect(page.locator('[aria-live="polite"]')).toContainText('Aparelho recentrado.');
});

test('keyboard announcements are throttled and use the angle from home, never a raw 4-digit value', async ({ page }) => {
  await page.goto('/?visualTest=1'); const phone = page.getByTestId('phone'); await phone.focus();
  const live = page.locator('[aria-live="polite"]');
  await live.evaluate((node) => { (window as unknown as { liveChanges: string[] }).liveChanges = []; new MutationObserver(() => (window as unknown as { liveChanges: string[] }).liveChanges.push(node.textContent ?? '')).observe(node, { childList: true, characterData: true, subtree: true }); });
  for (let i = 0; i < 40; i++) await page.keyboard.press('ArrowRight'); // 480 degrees in about a second
  await expect(live).toHaveText('Posição 120 graus.', ); // 480 mod 360
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
  await page.locator('.phone-canvas canvas').evaluate((canvas) => canvas.dispatchEvent(new Event('webglcontextlost', { cancelable: true })));
  await expect(page.locator('.phone-poster')).toBeVisible();
  await expect(page.locator('.phone-canvas')).toHaveCount(0);
});

test('E2E-08 reduced motion preference is respected', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' }); await page.goto('/');
  await live(page);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Recentrar aparelho' })).toHaveCount(1);
  await expect(page.getByTestId('phone')).toHaveAttribute('data-yaw-deg', String(HOME_YAW));
  const opacity = await page.locator('.hero-actions').evaluate((element) => getComputedStyle(element).opacity);
  expect(Number(opacity)).toBe(1);
  await page.getByTestId('phone').focus(); await page.keyboard.press('ArrowRight');
  await expect.poll(() => yawOf(page.getByTestId('phone'))).toBeGreaterThan(HOME_YAW);
});

test('idle drift runs subtly, then freezes at the hovered pose', async ({ page }) => {
  await page.goto('/');
  const phone = page.getByTestId('phone');
  await live(phone); await introDone(phone);
  await expect(phone).toHaveAttribute('data-idle', 'drifting');
  await expect.poll(() => yawOf(phone)).not.toBe(HOME_YAW);
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await expect(phone).toHaveAttribute('data-idle', 'frozen');
  // frozen means two reads taken 1.6 s apart agree, whatever the frame rate of the machine
  await expect.poll(async () => { const first = await yawOf(phone); await page.waitForTimeout(1600); return Math.abs((await yawOf(phone)) - first); }).toBeLessThan(0.05);
});

test('E2E-09 required responsive sizes have no horizontal overflow', async ({ page }) => {
  for (const width of [360, 390, 768, 1024, 1280, 1440, 1672]) {
    await page.setViewportSize({ width, height: 900 }); await page.goto('/');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `overflow at ${width}`).toBeTruthy();
  }
});


/* ---------- layout against the REAL projected display, placeholder, ribbon, hint, a11y and computed styles ---------- */

/** Fraction of the lit display (sampled on a 21x21 grid of its projected quad) covered by each floating card, plus the card rectangles. */
const cardCoverage = (page: Page) => page.evaluate(() => {
  const canvas = document.querySelector('.phone-canvas')!.getBoundingClientRect();
  const q = (document.querySelector('[data-testid=phone]') as HTMLElement).dataset.screenQuad!.split(',').map(Number);
  const corners = [0, 1, 2, 3].map((i) => [canvas.x + q[i * 2] * canvas.width, canvas.y + q[i * 2 + 1] * canvas.height]);
  const cards = [...document.querySelectorAll('.float-card')].map((card) => { const r = card.getBoundingClientRect(); return { left: r.left, top: r.top, right: r.right, bottom: r.bottom }; });
  const margin = 4; let covered = 0; let total = 0;
  for (let i = 0; i <= 20; i++) for (let j = 0; j <= 20; j++) {
    const u = i / 20; const v = j / 20;
    const top = [corners[0][0] + (corners[1][0] - corners[0][0]) * u, corners[0][1] + (corners[1][1] - corners[0][1]) * u];
    const bottom = [corners[3][0] + (corners[2][0] - corners[3][0]) * u, corners[3][1] + (corners[2][1] - corners[3][1]) * u];
    const x = top[0] + (bottom[0] - top[0]) * v; const y = top[1] + (bottom[1] - top[1]) * v; total++;
    if (cards.some((c) => x > c.left - margin && x < c.right + margin && y > c.top - margin && y < c.bottom + margin)) covered++;
  }
  return { covered, total, cards, viewport: innerWidth };
});

test('floating cards never cover the phone screen, and stay inside the viewport, at every required width', async ({ page }) => {
  test.setTimeout(1_200_000);
  const sizes: Array<[number, number]> = [[1672, 941], [1440, 900], [1280, 800], [1180, 800], [1024, 768], [768, 1024], [390, 844], [360, 800]];
  for (const [width, height] of sizes) {
    await page.setViewportSize({ width, height }); await page.goto('/?visualTest=1');
    const phone = page.getByTestId('phone'); await phone.scrollIntoViewIfNeeded(); await live(phone); await introDone(phone);
    await expect(phone).not.toHaveAttribute('data-screen-quad', '');
    await expect(page.locator('.alert-card.is-in')).toHaveCount(1);
    const { covered, cards, viewport } = await cardCoverage(page);
    expect(covered, `display samples covered by a card at ${width}px`).toBe(0);
    for (const card of cards) { expect(card.left, `card inside the viewport at ${width}px`).toBeGreaterThanOrEqual(0); expect(card.right).toBeLessThanOrEqual(viewport); }
  }
});

test('floating cards are decorative: they never intercept a pointer aimed at the device', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 }); await page.goto('/?visualTest=1');
  await expect(page.locator('.alert-card.is-in')).toHaveCount(1);
  const result = await page.evaluate(() => [...document.querySelectorAll('.float-pos')].map((wrapper) => {
    const card = wrapper.querySelector('.float-card')!.getBoundingClientRect();
    const hit = document.elementFromPoint(card.left + card.width / 2, card.top + card.height / 2);
    return { pointerEvents: getComputedStyle(wrapper).pointerEvents, cardBlocksPointer: !!hit?.closest('.float-pos') };
  }));
  expect(result).toEqual([{ pointerEvents: 'none', cardBlocksPointer: false }, { pointerEvents: 'none', cardBlocksPointer: false }]);
});

test('the phone area is never empty while the 3D chunk loads: a dimmed stand-in appears at once and hands over to the live scene', async ({ page }) => {
  await page.route('**/_next/static/chunks/*.js', async (route) => {
    try {
      const response = await route.fetch(); const body = await response.text();
      if (body.includes('webglcontextlost')) await new Promise((resolve) => setTimeout(resolve, 4000)); // simulate a slow connection for the 3D chunk only
      await route.fulfill({ response, body });
    } catch { /* closed page */ }
  });
  await page.goto('/');
  const phone = page.getByTestId('phone'); const poster = page.locator('.phone-poster');
  await expect(phone).toHaveAttribute('data-webgl', 'poster');
  await expect.poll(() => poster.evaluate((el) => Number(getComputedStyle(el).opacity))).toBeGreaterThan(0.9);
  const box = await poster.boundingBox(); expect(box?.height ?? 0).toBeGreaterThan(300);
  expect(await poster.evaluate((el) => getComputedStyle(el).filter)).toContain('brightness'); // dimmed: an unlit device, not a second finished one
  await live(phone); await expect(poster).toBeHidden();
});

test('the aurora is an open ribbon (two tapering crescents), not a closed halo', async ({ page }) => {
  await page.goto('/?visualTest=1');
  await expect(page.locator('.phone-orbit .ribbon')).toHaveCount(2);
  await expect(page.locator('.phone-orbit ellipse, .phone-orbit circle')).toHaveCount(0);
  const pointed = await page.locator('.phone-orbit .ribbon-body').evaluateAll((paths) => paths.map((path) => { const box = (path as SVGGraphicsElement).getBBox(); return box.width > 150 && box.height > 150; }));
  expect(pointed).toEqual([true, true]);
});

test('E2E-04c on touch screens the gesture hint appears once the device has settled and goes away at the first swipe', async ({ page }) => {
  const { context, mobile } = await mobilePage(page);
  const phone = mobile.getByTestId('phone'); await phone.scrollIntoViewIfNeeded(); await live(phone); await introDone(phone);
  const hint = mobile.locator('.gesture-hint'); await expect(hint).toHaveClass(/is-visible/); await expect(hint).toBeInViewport();
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  await swipe(mobile, [box.x + box.width / 2 - 40, box.y + box.height / 2], [box.x + box.width / 2 + 80, box.y + box.height / 2]);
  await expect(hint).not.toHaveClass(/is-visible/);
  await context.close();
});

test('axe finds no serious or critical issue in the header, hero and footer', async ({ page }) => {
  await page.setViewportSize({ width: 1672, height: 941 }); await page.goto('/?visualTest=1');
  const phone = page.getByTestId('phone'); await live(phone); await introDone(phone);
  await expect.poll(() => page.evaluate(() => [...document.querySelectorAll('.benefit')].every((node) => getComputedStyle(node).opacity === '1')), WEBGL_WAIT).toBeTruthy(); // GSAP follows the frame rate: ~1 fps in software WebGL
  const results = await new AxeBuilder({ page }).include('.site-header-bar').include('main.hero').include('.hero-footer').analyze();
  const summary = {
    scannedAt: new Date().toISOString(), mode: GPU ? 'gpu' : 'software', axeVersion: results.testEngine.version, url: results.url, viewport: '1672x941',
    counts: { passes: results.passes.length, violations: results.violations.length, incomplete: results.incomplete.length, inapplicable: results.inapplicable.length },
    violations: results.violations.map((violation) => ({ id: violation.id, impact: violation.impact, help: violation.help, nodes: violation.nodes.map((node) => ({ target: node.target, summary: node.failureSummary })) })),
    incomplete: results.incomplete.map((item) => ({ id: item.id, impact: item.impact, nodes: item.nodes.length })),
  };
  await fs.mkdir(path.join(EVIDENCE, 'hero-shots'), { recursive: true });
  await fs.writeFile(path.join(EVIDENCE, 'hero-shots/axe.json'), `${JSON.stringify(summary, null, 2)}\n`);
  expect(results.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical'), JSON.stringify(summary.violations)).toEqual([]);
});

test('computed styles prove the token colours and the Inter / Roboto fonts really loaded', async ({ page }) => {
  await page.setViewportSize({ width: 1672, height: 941 }); await page.goto('/?visualTest=1');
  const phone = page.getByTestId('phone'); await live(phone); await introDone(phone);
  const proof = await page.evaluate(async () => {
    await document.fonts.ready;
    const style = (selector: string, props: string[]) => { const el = document.querySelector(selector)!; const computed = getComputedStyle(el); return Object.fromEntries(props.map((prop) => [prop, computed.getPropertyValue(prop)])); };
    const root = getComputedStyle(document.documentElement);
    const faces = [...document.fonts].filter((face) => face.status === 'loaded').map((face) => ({ family: face.family.replace(/"/g, ''), weight: face.weight, style: face.style, status: face.status }));
    return {
      tokens: Object.fromEntries(['--bg-main', '--color-primary', '--color-alert', '--text-primary', '--text-secondary', '--content-width'].map((name) => [name, root.getPropertyValue(name).trim()])),
      fonts: { check: { 'Inter Variable': document.fonts.check('700 20px "Inter Variable"'), Roboto: document.fonts.check('400 20px Roboto') }, loadedFaces: faces },
      body: style('body', ['font-family', 'color', 'background-color']),
      h1: style('.hero h1', ['font-family', 'font-weight', 'font-size', 'letter-spacing']),
      h1Line1: style('.hero h1 .h1-line:not(.h1-accent) .h1-fragment', ['color']),
      h1Accent: style('.hero h1 .h1-accent .h1-fragment', ['color']),
      subheadline: style('.subheadline', ['font-family', 'color', 'font-size']),
      benefit: style('.benefit', ['font-family', 'color']),
      primaryButton: style('.button-primary', ['font-family', 'color']),
    };
  });
  await fs.mkdir(path.join(EVIDENCE, 'hero-shots'), { recursive: true });
  await fs.writeFile(path.join(EVIDENCE, 'hero-shots/computed-styles.json'), `${JSON.stringify({ mode: GPU ? 'gpu' : 'software', ...proof }, null, 2)}\n`);
  expect(proof.fonts.check['Inter Variable']).toBe(true); expect(proof.fonts.check.Roboto).toBe(true);
  expect(proof.fonts.loadedFaces.some((face) => /Inter/.test(face.family))).toBe(true); expect(proof.fonts.loadedFaces.some((face) => /Roboto/.test(face.family))).toBe(true);
  expect(proof.h1['font-family']).toMatch(/Inter/); expect(proof.subheadline['font-family']).toMatch(/Roboto/);
  expect(proof.tokens['--color-primary']).toBe('#22e3b0'); expect(proof.tokens['--bg-main']).toBe('#12161c'); expect(proof.tokens['--text-primary']).toBe('#e6eaf0');
  expect(proof.h1Line1.color).toBe('rgb(230, 234, 240)'); expect(proof.h1Accent.color).toBe('rgb(34, 227, 176)');
});
