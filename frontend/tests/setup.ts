import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { resetCatalogCache } from '../src/hooks/useCatalog';
import { resetMockDb } from './mocks/handlers';
import { server } from './mocks/server';

// Los handlers responden en http://localhost:3000/api/...
if (window.location.origin !== 'http://localhost:3000') {
  window.history.replaceState(null, '', 'http://localhost:3000/');
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetMockDb();
  sessionStorage.clear();
  resetCatalogCache();
});

afterAll(() => server.close());
