import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  workers: 1,
  reporter: 'list',
  use: { baseURL: 'http://127.0.0.1:5190', trace: 'retain-on-failure' },
  projects: [
    { name: 'chromium-desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'chromium-mobile', use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 } } },
  ],
  webServer: {
    command: process.env.QMH_E2E_BUILT_UI === 'true'
      ? 'node ../../tools/local-test/serve.mjs'
      : 'npm run dev -- --host 127.0.0.1 --port 5190 --strictPort',
    url: 'http://127.0.0.1:5190', reuseExistingServer: process.env.QMH_E2E_BUILT_UI === 'true', env: { QMH_WEB_PORT: '5190' },
  },
})
