import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/process-management/api': { target: process.env.PROCESS_API_TARGET || 'http://localhost:8080', changeOrigin: true } } },
  test: { environment: 'jsdom', setupFiles: './src/test-setup.js', restoreMocks: true, include: ['src/**/*.test.{js,jsx}'] },
});
