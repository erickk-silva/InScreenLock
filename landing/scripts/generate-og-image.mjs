import { chromium } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] });
const context = await browser.newContext({ viewport: { width: 1672, height: 878 }, deviceScaleFactor: 1200 / 1672 });
const page = await context.newPage();
await page.goto(process.env.OG_SOURCE_URL ?? 'http://127.0.0.1:3100/?visualTest=1', { waitUntil: 'networkidle' });
await page.getByRole('heading', { level: 1 }).waitFor();
await page.getByTestId('phone').waitFor();
await page.screenshot({ path: path.join(root, 'public/og-image.png'), animations: 'disabled' });
await browser.close();
console.log('public/og-image.png generated from the responsive 3D hero at 1200 × 630.');
