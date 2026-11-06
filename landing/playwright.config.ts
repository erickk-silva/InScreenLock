import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e', timeout: 45_000, expect: { timeout: 8_000 },
  use: { baseURL: 'http://127.0.0.1:3100', ...devices['Desktop Chrome'], locale: 'pt-BR', reducedMotion: 'no-preference', launchOptions: { executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] } },
  webServer: { command: 'npm run build && npm run serve -- --port 3100', url: 'http://127.0.0.1:3100', reuseExistingServer: false, timeout: 180_000 },
});
