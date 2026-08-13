import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        // Keep APM SSE streams open (no proxy idle timeout).
        timeout: 0,
        proxyTimeout: 0,
      },
    },
  },
});
