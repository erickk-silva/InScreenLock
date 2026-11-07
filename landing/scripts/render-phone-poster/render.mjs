// Renders the static WebGL-fallback poster from the SAME drawing code that feeds the live phone texture.
// Usage: npm run render-phone-poster
import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const require = createRequire(import.meta.url);
const ts = require('typescript');

const transpile = async (file) => ts.transpileModule(await fs.readFile(path.join(root, 'src/lib', file), 'utf8'), { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const motion = await transpile('phone-motion.ts');
const screen = (await transpile('phone-screen.ts')).replace(/from '\.\/phone-motion'/, 'from "data:text/javascript;base64,' + Buffer.from(motion).toString('base64') + '"');
const font = (await fs.readFile(path.join(root, 'node_modules/@fontsource-variable/inter/files/inter-latin-wght-normal.woff2'))).toString('base64');

const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH ?? '/usr/bin/google-chrome', args: ['--no-sandbox'] });
const page = await browser.newPage();
await page.setContent(`<style>@font-face{font-family:"Inter Variable";font-weight:100 900;src:url(data:font/woff2;base64,${font}) format("woff2")}</style><canvas id="c" width="788" height="1584"></canvas>`);
await page.evaluate(async () => { await Promise.all([300, 400, 500, 600, 700, 800].map((w) => document.fonts.load(`italic ${w} 20px "Inter Variable"`))); await document.fonts.ready; });
const dataUrl = await page.evaluate(async (source) => {
  const mod = await import(`data:text/javascript;base64,${btoa(unescape(encodeURIComponent(source)))}`);
  const canvas = document.getElementById('c'); const ctx = canvas.getContext('2d');
  mod.drawPhoneScreen(ctx, canvas.width, canvas.height);
  return canvas.toDataURL('image/webp', 0.9);
}, screen);
await browser.close();

const webp = Buffer.from(dataUrl.split(',')[1], 'base64');
await fs.writeFile(path.join(root, 'public/app-screens/dashboard-demo.webp'), webp);
const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 500 760"><defs><linearGradient id="body" x2="1" y2="1"><stop stop-color="#8d98a3"/><stop offset=".16" stop-color="#252d35"/><stop offset=".8" stop-color="#0c1116"/><stop offset="1" stop-color="#7d8a96"/></linearGradient><clipPath id="screen"><rect x="91.5" y="61" width="317" height="638" rx="36"/></clipPath><filter id="glow"><feGaussianBlur stdDeviation="14"/></filter></defs><ellipse cx="240" cy="722" rx="190" ry="26" fill="#22e3b0" opacity=".45" filter="url(#glow)"/><g transform="translate(250 380) skewX(-12) translate(-250 -380)"><rect x="77.5" y="35" width="345" height="690" rx="54" fill="url(#body)" stroke="#9aa6b1" stroke-width="2.5"/><rect x="85" y="43" width="330" height="674" rx="47" fill="#05080c"/><image href="data:image/webp;base64,${webp.toString('base64')}" x="91.5" y="61" width="317" height="638" preserveAspectRatio="xMidYMid slice" clip-path="url(#screen)"/><circle cx="250" cy="75" r="6" fill="#010203" stroke="#2a323a" stroke-width="2"/></g></svg>\n`;
await fs.writeFile(path.join(root, 'public/app-screens/phone-poster.svg'), svg);
console.log(`poster written: ${webp.length} bytes webp`);
