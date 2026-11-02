import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { resetCatalogCache } from '../src/hooks/useCatalog';
import { resetMockDb } from './mocks/handlers';
import { server } from './mocks/server';

// Los handlers responden en http://localhost:3000/api/...
if (window.location.origin !== 'http://localhost:3000') {
  window.history.replaceState(null, '', 'http://localhost:3000/');
}

// findBy y waitFor esperan hasta 3 s en vez de 1 s: con la suite completa en paralelo, una pantalla
// que carga varias cosas puede tardar más de 1 s en una máquina cargada. Sigue por debajo de los 5 s
// que Vitest le da a cada test
configure({ asyncUtilTimeout: 3000 });

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetMockDb();
  sessionStorage.clear();
  resetCatalogCache();
});

afterAll(() => server.close());
