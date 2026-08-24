import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev proxies /api and /ws to the Nginx gateway so the browser sees a single
// origin — identical to production, so no CORS surprises at deploy time.
export default defineConfig({
  plugins: [react()],
  define: { global: 'window' },   // sockjs-client expects a Node-style global
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_GATEWAY || 'http://localhost', changeOrigin: true },
      '/ws': { target: process.env.VITE_GATEWAY || 'http://localhost', ws: true, changeOrigin: true },
    },
  },
});
