import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath } from 'url';
import path from 'path';

const root = fileURLToPath(new URL('./', import.meta.url));

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      // The Quarkus backend already serves every REST resource under /api/*
      // (root-path baked into the build), so the proxy is a straight pass-through.
      // Do NOT add a rewrite that strips /api — the back returns 404 without it.
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
