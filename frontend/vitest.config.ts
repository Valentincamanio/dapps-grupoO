import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['tests/setup.ts'],
    include: ['tests/**/*.test.{ts,tsx}'],
    css: { modules: { classNameStrategy: 'non-scoped' } },
    // Con la suite completa en paralelo, en una máquina cargada una pantalla que carga varias cosas
    // puede pasar los 5 s por defecto. Va junto con la espera de 3 s de tests/setup.ts
    testTimeout: 15_000,
    // El reporte JUnit lo publica el CI como artifact.
    reporters: ['default', 'junit'],
    outputFile: { junit: 'test-results/junit.xml' },
  },
});
