import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, the page and the API share one origin (localhost:5173): Vite forwards /api and /ws to the server.
// The Host header is kept, so the server sees a same-origin request and needs no CORS for it.
const server = process.env.CARO_SERVER ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: server },
      '/ws': { target: server.replace(/^http/, 'ws'), ws: true },
    },
  },
});
