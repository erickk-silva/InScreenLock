import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const evidence = path.resolve(process.cwd(), '../.agent-harness/artifacts/ISL-LANDING-001/20261106T200700Z/sections');

test('E2E-09 sections and anchors fit each required viewport', async ({ page }) => {
  for (const width of [360, 390, 768, 1440, 1672]) {
    await page.setViewportSize({ width, height: width < 600 ? 844 : 940 });
    await page.goto('/?visualTest=1');
    await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    for (const id of ['como-funciona', 'demo', 'recursos', 'privacidade', 'resultados', 'faq', 'download']) await expect(page.locator(`#${id}`)).toHaveCount(1);
  }
});

test('hero demonstration link targets the interactive calculator and PIN section', async ({ page }) => {
  await page.goto('/?visualTest=1');
  await page.locator('.hero a[href="#demo"]').click();
  await expect(page).toHaveURL(/#demo$/);
  await expect(page.locator('#demo')).toHaveClass(/demo-lab/);
  await expect(page.locator('#daily-minutes')).toBeVisible();
});

test('E2E-10 calculator projects a hypothetical 60 minute day', async ({ page }) => {
  await page.goto('/?visualTest=1');
  await page.locator('#daily-minutes').fill('60');
  await expect(page.getByText('365', { exact: true })).toBeVisible();
  await expect(page.getByText('15,2', { exact: true })).toBeVisible();
  await expect(page.getByText('Cenário hipotético')).toBeVisible();
});

test('E2E-11 limit demo says four minutes over the target', async ({ page }) => {
  await page.goto('/?visualTest=1');
  await expect(page.getByText('4 min acima da meta')).toBeVisible();
  await expect(page.getByText('Dados fictícios', { exact: true }).last()).toBeVisible();
});

test('E2E-12 FAQ supports keyboard activation and expanded state', async ({ page }) => {
  await page.goto('/?visualTest=1');
  const faq = page.locator('#faq button').nth(1);
  await faq.focus(); await page.keyboard.press('Enter');
  await expect(faq).toHaveAttribute('aria-expanded', 'true');
  await expect(page.locator(`#${await faq.getAttribute('aria-controls')}`)).toBeVisible();
});

test('E2E-13 release fixtures cover preparing and available without fake APK claims', async ({ page }) => {
  await page.goto('/?visualTest=1');
  await expect(page.locator('#download [data-release-status="preparing"]')).toContainText('Download em preparação');
  await page.goto('/?visualTest=1&releaseFixture=available');
  const release = page.locator('#download [data-release-status="available"]');
  await expect(release).toContainText('0.1.0-demo');
  await expect(release.locator('a[href="https://example.invalid/InScreenLock-demo.apk"]')).toHaveCount(1);
  await expect(release).toContainText('SHA-256');
});

test('E2E-14 renders the basic copy when JavaScript is disabled', async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false, viewport: { width: 390, height: 844 } });
  const page = await context.newPage(); await page.goto('http://127.0.0.1:3100/');
  await expect(page.getByRole('heading', { name: 'Você abriu por cinco minutos. O feed tinha outros planos.' })).toBeVisible();
  await expect(page.locator('#download')).toContainText('Download em preparação');
  await expect(page.locator('#faq')).toContainText('O que o InScreenLock mede?');
  await context.close();
});

test('section visual evidence at desktop, tablet and mobile widths', async ({ page }) => {
  await fs.mkdir(evidence, { recursive: true });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const sections = ['como-funciona', 'demo', 'recursos', 'privacidade', 'resultados', 'faq', 'download'];
  for (const width of [1672, 1440, 768, 390]) {
    await page.setViewportSize({ width, height: width < 600 ? 844 : 941 });
    await page.goto('/?visualTest=1');
    for (const section of sections) {
      const target = page.locator(`#${section}`); await target.evaluate((element) => element.scrollIntoView({ block: 'start' })); await page.evaluate(() => window.scrollBy(0, -105));
      if (section === 'recursos') await expect(target.locator('.magic-bento-grid')).toBeVisible();
      await page.waitForTimeout(800);
      await target.screenshot({ path: path.join(evidence, `${section}-${width}.png`), animations: 'disabled', style: '.site-header-bar { visibility: hidden !important; }' });
    }
  }
});
