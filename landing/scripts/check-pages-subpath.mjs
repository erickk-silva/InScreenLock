import { chromium } from '@playwright/test';

const origin = process.env.PAGES_PREVIEW_URL ?? 'http://127.0.0.1:3120/InScreenLock/';
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 900 }, reducedMotion: 'reduce' });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
await page.goto(`${origin}?visualTest=1`, { waitUntil: 'networkidle' });
const checks = {
  title: (await page.title()).includes('InScreenLock'),
  hero: await page.getByRole('heading', { level: 1 }).count() === 1,
  release: await page.locator('#download').innerText().then(text => text.includes('Download em preparação')),
  anchors: await page.locator('#como-funciona,#demo,#recursos,#privacidade,#resultados,#faq,#download').count() === 7,
  heroPoster: await page.locator('.phone-poster').getAttribute('src').then(src => src?.startsWith('/InScreenLock/app-screens/phone-poster.webp')),
  posterServed: await page.request.get(new URL('app-screens/phone-poster.webp', origin).toString()).then(response => response.ok()),
  noTestReleaseFixture: await Promise.all((await page.locator('script[src]').evaluateAll(nodes => nodes.map(node => node.src))).map(src => page.request.get(src).then(response => response.text()))).then(sources => sources.every(source => !source.includes('example.invalid'))),
  ogImage: await page.request.get(new URL('og-image.png', origin).toString()).then(response => response.ok()),
  errors: errors.length === 0
};
await browser.close();
for (const [name, passed] of Object.entries(checks)) console.log(`${passed ? 'PASS' : 'FAIL'} ${name}`);
if (Object.values(checks).some(passed => !passed)) process.exit(1);
