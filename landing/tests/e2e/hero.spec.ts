import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const HOME_YAW = -17; // approved hero pose, see INITIAL_POSE

test('E2E-01 hero headline, download state, and no horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 1672, height: 941 });
  await page.goto('/?visualTest=1');
  await expect(page.getByRole('heading', { level: 1, name: 'Seu feed não tem fim. Seu tempo tem.' })).toBeVisible();
  await expect(page.getByText('Baixar para Android')).toBeVisible();
  await expect(page.getByText('Download em preparação. O APK será publicado após verificação.')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test('E2E-02 and 03 mouse drag and accumulated turns remain unbounded while pitch clamps', async ({ page }) => {
  await page.goto('/?visualTest=1');
  const phone = page.getByTestId('phone'); await expect(phone).toBeVisible();
  const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  const x = box.x + 50; const y = box.y + box.height / 2;
  await page.mouse.move(x, y); await page.mouse.down(); await page.mouse.move(x + 330, y, { steps: 8 }); await page.mouse.up();
  await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg'))).toBeGreaterThan(100);
  await expect(page.locator('[aria-live="polite"]')).toContainText('Giro liberado.');
  const releaseYaw = Number(await phone.getAttribute('data-yaw-deg'));
  await expect.poll(async () => Math.abs(Number(await phone.getAttribute('data-yaw-deg')) - releaseYaw)).toBeGreaterThan(0.1);
  await phone.evaluate((root) => {
    const target = root as HTMLDivElement & { setPointerCapture: () => void; hasPointerCapture: () => boolean; releasePointerCapture: () => void };
    target.setPointerCapture = () => {}; target.hasPointerCapture = () => true; target.releasePointerCapture = () => {};
    const send = (type: string, clientX: number, clientY: number) => target.dispatchEvent(new PointerEvent(type, { pointerId: 78, pointerType: 'mouse', button: 0, clientX, clientY, bubbles: true }));
    send('pointerdown', 100, 100); send('pointermove', 760, 100); send('pointermove', 1420, 100); send('pointerup', 1420, 100);
  });
  await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg'))).toBeGreaterThan(600);
  await phone.evaluate((root) => {
    const target = root as HTMLDivElement & { setPointerCapture: () => void; hasPointerCapture: () => boolean; releasePointerCapture: () => void };
    const send = (type: string, clientY: number) => target.dispatchEvent(new PointerEvent(type, { pointerId: 79, pointerType: 'mouse', button: 0, clientX: 100, clientY, bubbles: true }));
    send('pointerdown', 100); send('pointermove', 1100); send('pointerup', 1100);
  });
  await expect.poll(async () => Math.abs(Number(await phone.getAttribute('data-pitch-deg')))).toBeLessThanOrEqual(30);
});

test('E2E-04 touch drag responds on mobile', async ({ page }) => {
  const browser = page.context().browser(); if (!browser) throw new Error('browser missing');
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1, isMobile: true, hasTouch: true });
  const mobile = await context.newPage(); await mobile.goto('http://127.0.0.1:3100/?visualTest=1');
  const phone = mobile.getByTestId('phone'); await phone.scrollIntoViewIfNeeded(); const box = await phone.boundingBox(); if (!box) throw new Error('phone missing');
  const cdp = await context.newCDPSession(mobile);
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: box.x + box.width / 2, y: box.y + box.height / 2 }] });
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: box.x + box.width / 2 + 160, y: box.y + box.height / 2 }] });
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await expect.poll(async () => Number(await mobile.getByTestId('phone').getAttribute('data-yaw-deg'))).toBeGreaterThan(HOME_YAW);
  await context.close();
});

test('E2E-05 page scrolling remains available outside the phone', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 800 }); await page.goto('/');
  await page.mouse.move(100, 500); await page.mouse.wheel(0, 400); await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(0);
});

test('E2E-06 reset and keyboard rotation work', async ({ page }) => {
  await page.goto('/?visualTest=1'); const phone = page.getByTestId('phone'); await phone.focus(); await page.keyboard.press('ArrowRight');
  await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg'))).toBeGreaterThan(HOME_YAW);
  await page.keyboard.press('Home'); await expect.poll(async () => Number(await phone.getAttribute('data-yaw-deg')), { timeout: 20_000 }).toBe(HOME_YAW);
  await page.getByRole('button', { name: 'Recentrar aparelho' }).click();
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
  await expect(page.getByRole('button', { name: 'Recentrar aparelho' })).toBeVisible();
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

test('visual evidence: desktop, mobile, and phone orientations', async ({ page }) => {
  test.setTimeout(240_000);
  const output = path.resolve(process.cwd(), '../.agent-harness/artifacts/ISL-LANDING-001/20261106T200700Z/hero-shots');
  await fs.mkdir(output, { recursive: true });
  await page.setViewportSize({ width: 1672, height: 941 }); await page.goto('/?visualTest=1');
  await page.evaluate(() => document.fonts.ready); await expect(page.getByTestId('phone')).toHaveAttribute('data-webgl', 'live', { timeout: 60_000 }); await page.waitForTimeout(3500); // text effects (GSAP / motion) settle; the phone is already in its final frame in visualTest mode
  await expect(page.locator('.phone-poster')).toHaveAttribute('alt', /demonstração fictícia/i);
  await page.screenshot({ path: path.join(output, 'actual-1672.png') });
  const phone = page.getByTestId('phone'); await phone.focus();
  await page.screenshot({ path: path.join(output, 'phone-front.png') });
  const settleAt = async (target: number) => { await expect.poll(async () => Math.abs(Number(await phone.getAttribute('data-yaw-deg')) - target), { timeout: 60_000 }).toBeLessThan(3); await page.waitForTimeout(800); };
  for (let i = 0; i < 8; i++) await page.keyboard.press('ArrowRight');
  await settleAt(HOME_YAW + 96); await page.screenshot({ path: path.join(output, 'phone-side.png') });
  for (let i = 0; i < 9; i++) await page.keyboard.press('ArrowRight');
  await settleAt(HOME_YAW + 204); await page.screenshot({ path: path.join(output, 'phone-back.png') });
  for (const [name, width, height] of [['1440', 1440, 900], ['390', 390, 844], ['360', 360, 800]] as const) {
    await page.setViewportSize({ width, height }); await page.goto('/?visualTest=1'); await page.waitForTimeout(3500);
    await page.screenshot({ path: path.join(output, `actual-${name}.png`) });
    if (width < 768) { // on phones the device sits below the copy: scroll to it (the 3D scene mounts on first sight) and capture it too
      await page.getByTestId('phone').scrollIntoViewIfNeeded(); await expect(page.getByTestId('phone')).toHaveAttribute('data-webgl', 'live', { timeout: 60_000 }); await page.waitForTimeout(3000);
      await page.screenshot({ path: path.join(output, `actual-${name}-phone.png`) });
    }
  }
  const actual = await fs.readFile(path.join(output, 'actual-1672.png'));
  const reference = await fs.readFile(path.resolve(process.cwd(), '../.agent-harness/input/InScreenLock_Landing_Spec_Handoff_v1.0/references/01-HERO-APROVADO.png'));
  await page.setViewportSize({ width: 1672, height: 941 });
  await page.setContent('<canvas width="1672" height="941" style="width:1672px;height:941px"></canvas>');
  await page.evaluate(({ left, right }) => {
    const canvas = document.querySelector('canvas')!; const ctx = canvas.getContext('2d')!;
    const draw = (src: string) => new Promise<HTMLImageElement>((resolve) => { const image = new Image(); image.onload = () => resolve(image); image.src = `data:image/png;base64,${src}`; });
    return Promise.all([draw(left), draw(right)]).then(([a, b]) => { ctx.globalAlpha = .5; ctx.drawImage(a, 0, 0, canvas.width, canvas.height); ctx.drawImage(b, 0, 0, canvas.width, canvas.height); });
  }, { left: actual.toString('base64'), right: reference.toString('base64') });
  await page.screenshot({ path: path.join(output, 'overlay-1672.png') });
});
