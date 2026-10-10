// Único lugar de la app que usa fetch (research R-5 y R-14). No importa React.
import type { ApiError } from '../types/api';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

export type QueryValue = string | number | boolean | null | undefined;

export interface RequestOptions {
  // Objeto plano de parámetros (p. ej. PlayerQuery); los valores vacíos se omiten
  query?: object;
  body?: unknown;
  signal?: AbortSignal;
}

export interface AuthHandlers {
  getToken: () => string | null;
  onUnauthorized: () => void;
}

const NETWORK_ERROR_MESSAGE = 'no se pudo conectar con la pizarra';

// Error que lanzan todas las llamadas; el detalle del backend queda en apiError
export class ApiRequestError extends Error {
  readonly apiError: ApiError;

  constructor(apiError: ApiError) {
    super(apiError.message);
    this.name = 'ApiRequestError';
    this.apiError = apiError;
  }
}

// Sin handlers registrados no hay token y un 401 no hace nada extra
let authHandlers: AuthHandlers = {
  getToken: () => null,
  onUnauthorized: () => {},
};

// Lo llama el SessionContext para que el cliente lea el token sin depender de React
export function setAuthHandlers(handlers: AuthHandlers): void {
  authHandlers = handlers;
}

function buildUrl(path: string, query?: RequestOptions['query']): URL {
  const url = new URL('/api' + path, window.location.origin);
  if (query) {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query) as [string, QueryValue][]) {
      // Los filtros vacíos no viajan
      if (value === undefined || value === null || value === '') continue;
      params.append(key, String(value));
    }
    url.search = params.toString();
  }
  return url;
}

// Error armado en el cliente cuando no hay respuesta útil del backend
function networkError(path: string): ApiRequestError {
  return new ApiRequestError({
    timestamp: new Date().toISOString(),
    status: 0,
    error: 'Network Error',
    message: NETWORK_ERROR_MESSAGE,
    path,
  });
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}

function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiError).status === 'number' &&
    typeof (value as ApiError).message === 'string'
  );
}

export async function request<T>(
  method: HttpMethod,
  path: string,
  { query, body, signal }: RequestOptions = {},
): Promise<T> {
  const url = buildUrl(path, query);
  const token = authHandlers.getToken();

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch (error) {
    // Una cancelación no es un error de red: se relanza tal cual
    if (isAbortError(error)) throw error;
    throw networkError(path);
  }

  // Un 401 de una llamada con token significa sesión vencida o inválida
  if (response.status === 401 && token) {
    authHandlers.onUnauthorized();
  }

  if (response.status === 204) {
    return undefined as T;
  }

  let data: unknown;
  try {
    data = await response.json();
  } catch (error) {
    if (isAbortError(error)) throw error;
    throw networkError(path);
  }

  if (!response.ok) {
    if (!isApiError(data)) throw networkError(path);
    throw new ApiRequestError(data);
  }

  return data as T;
}
