import { defineConfig } from '@playwright/test';
export default defineConfig({ testDir:'./tests',workers:1,timeout:90000,use:{baseURL:process.env.ARO_WEB_URL||'http://127.0.0.1:5173',headless:true,channel:process.env.PLAYWRIGHT_CHANNEL||'chrome',screenshot:'only-on-failure',trace:'retain-on-failure'},reporter:'list' });
