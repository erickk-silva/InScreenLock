import { defineConfig, devices } from '@playwright/test';

/**
 * Default: headless Chrome with software WebGL (about 1 fps for the 3D scene, so every wait in the suite follows data attributes, never a fixed delay).
 * `E2E_GPU=1`: headed system Chrome on the hardware GPU through WSLg (renderer "ANGLE ... D3D12 ..."), ~50 fps; used for visual evidence, motion video and FPS.
 * Headed windows appear on the desktop, so the GPU run is meant to be short and sequential.
 */
const gpu = process.env.E2E_GPU === '1';

export default defineConfig({
  testDir: './tests/e2e',
  // deterministic waits (data attributes, polls) with generous ceilings: a slow or busy machine makes a test slower, not flaky
  timeout: 240_000,
  expect: { timeout: 30_000 },
  workers: 1,
  fullyParallel: false,
  use: {
    baseURL: 'http://127.0.0.1:3100', ...devices['Desktop Chrome'], locale: 'pt-BR', reducedMotion: 'no-preference',
    launchOptions: gpu
      ? { executablePath: '/usr/bin/google-chrome', headless: false, env: { ...process.env, GALLIUM_DRIVER: 'd3d12' } as Record<string, string> }
      : { executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] },
  },
  webServer: { command: 'npm run build && npm run serve -- --port 3100', url: 'http://127.0.0.1:3100', reuseExistingServer: false, timeout: 300_000 },
});
