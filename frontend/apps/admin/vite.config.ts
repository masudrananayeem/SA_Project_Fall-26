import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Local dev: /api is proxied to the Spring Boot backend, so no CORS setup is needed.
// Production (Vercel): set VITE_API_URL to the backend origin (Cloudflare Worker URL).
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true, proxy: { '/api': 'http://localhost:8080' } },
  preview: { port: 5173 },
  build: { chunkSizeWarningLimit: 1200 },
});
