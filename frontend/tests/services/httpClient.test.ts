import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError, request, setAuthHandlers } from '../../src/services/httpClient';
import { server } from '../mocks/server';

const NO_AUTH = { getToken: () => null, onUnauthorized: () => {} };

afterEach(() => {
  setAuthHandlers(NO_AUTH);
});

async function captureRequest(path: string, run: () => Promise<unknown>): Promise<Request> {
  let captured: Request | undefined;
  server.use(
    http.get(`*/api${path}`, ({ request: incoming }) => {
      captured = incoming.clone();
      return HttpResponse.json({});
    }),
  );
  await run();
  return captured as Request;
}

describe('httpClient', () => {
  it('agrega Authorization solo cuando hay token', async () => {
    setAuthHandlers({ getToken: () => 'abc123', onUnauthorized: () => {} });

    const conToken = await captureRequest('/cosa', () => request('GET', '/cosa'));
    setAuthHandlers(NO_AUTH);
    const sinToken = await captureRequest('/cosa', () => request('GET', '/cosa'));

    expect(conToken.headers.get('Authorization')).toBe('Bearer abc123');
    expect(sinToken.headers.get('Authorization')).toBeNull();
  });

  it('omite los parámetros de query vacíos', async () => {
    const query = { league: 'LA_LIGA', team: '', position: undefined, size: 12, page: 0, otro: null };

    const captured = await captureRequest('/cosa', () => request('GET', '/cosa', { query }));

    const params = new URL(captured.url).searchParams;
    expect(Object.fromEntries(params)).toEqual({ league: 'LA_LIGA', size: '12', page: '0' });
  });

  it('prefija la ruta con /api', async () => {
    const captured = await captureRequest('/players', () => request('GET', '/players'));

    expect(new URL(captured.url).pathname).toBe('/api/players');
  });

  it('devuelve undefined cuando la respuesta es 204', async () => {
    server.use(http.put('*/api/cosa', () => new HttpResponse(null, { status: 204 })));

    const result = await request('PUT', '/cosa', { body: { a: 1 } });

    expect(result).toBeUndefined();
  });

  it('lanza ApiRequestError con el ApiError del cuerpo', async () => {
    const apiError = {
      timestamp: '2026-10-01T10:00:00Z',
      status: 404,
      error: 'Not Found',
      message: 'No existe un jugador con id 999999.',
      path: '/players/999999',
    };
    server.use(http.get('*/api/players/999999', () => HttpResponse.json(apiError, { status: 404 })));

    const failure = await request('GET', '/players/999999').catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(ApiRequestError);
    expect((failure as ApiRequestError).apiError).toEqual(apiError);
  });

  it('un error de red da status 0 y el mensaje de conexión', async () => {
    server.use(http.get('*/api/cosa', () => HttpResponse.error()));

    const failure = await request('GET', '/cosa').catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(ApiRequestError);
    expect((failure as ApiRequestError).apiError.status).toBe(0);
    expect((failure as ApiRequestError).apiError.message).toBe('no se pudo conectar con la pizarra');
  });

  it('un cuerpo que no es JSON da status 0 y el mensaje de conexión', async () => {
    server.use(http.get('*/api/cosa', () => new HttpResponse('<html>caído</html>', { status: 502 })));

    const failure = await request('GET', '/cosa').catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(ApiRequestError);
    expect((failure as ApiRequestError).apiError.status).toBe(0);
    expect((failure as ApiRequestError).apiError.message).toBe('no se pudo conectar con la pizarra');
  });

  it('un 401 con token llama a onUnauthorized', async () => {
    const onUnauthorized = vi.fn();
    setAuthHandlers({ getToken: () => 'vencido', onUnauthorized });
    server.use(
      http.get('*/api/auth/me', () =>
        HttpResponse.json(
          { timestamp: 'x', status: 401, error: 'Unauthorized', message: 'La credencial es inválida.', path: '/auth/me' },
          { status: 401 },
        ),
      ),
    );

    await request('GET', '/auth/me').catch(() => undefined);

    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('un 401 sin token no llama a onUnauthorized', async () => {
    const onUnauthorized = vi.fn();
    setAuthHandlers({ getToken: () => null, onUnauthorized });

    const failure = await request('POST', '/auth/login', {
      body: { username: 'nadie', password: 'incorrecta' },
    }).catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(ApiRequestError);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});
