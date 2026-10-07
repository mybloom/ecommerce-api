import { defineConfig } from '@playwright/test';
import { env } from './core/env';

export default defineConfig({
  testDir: './tests',
  globalSetup: './global-setup.ts',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: env.apiBaseUrl,
    extraHTTPHeaders: { 'Content-Type': 'application/json' },
    // 요청·응답은 core/ApiClient.ts 가 카드번호를 가려 첨부로 남긴다.
    // trace 는 요청 본문을 그대로 담아 카드번호가 평문으로 남으므로 켜지 않는다
    trace: 'off',
  },
});
