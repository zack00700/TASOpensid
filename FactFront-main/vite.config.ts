import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath } from 'url';
import path from 'path';

const root = fileURLToPath(new URL('./', import.meta.url));

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(root, 'src'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    root,
  },
});
