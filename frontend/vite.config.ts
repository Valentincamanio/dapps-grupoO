import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// El backend no habilita CORS: en desarrollo todo pedido a /api pasa por el proxy de Vite
// y llega a :8080 sin el prefijo /api.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      },
    },
  },
});
