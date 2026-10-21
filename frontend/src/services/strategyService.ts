// Administración de estrategias (specs/007-cotizacion-estrategias/contracts/strategies-api.yaml).
// Solo ADMIN: sin sesión responde 401 y con un usuario común, 403.
import type { RequoteTiming, StrategyChange, StrategyDto, StrategyInput } from '../types/quotes';
import { request } from './httpClient';

// Crea la versión 1, inactiva
export function createStrategy(name: string, input: StrategyInput, signal?: AbortSignal): Promise<StrategyDto> {
  return request<StrategyDto>('POST', '/strategies', { body: { name, ...input }, signal });
}

// En orden de creación, cada una con todas sus versiones
export function listStrategies(signal?: AbortSignal): Promise<StrategyDto[]> {
  return request<StrategyDto[]>('GET', '/strategies', { signal });
}

export function getStrategy(id: number, signal?: AbortSignal): Promise<StrategyDto> {
  return request<StrategyDto>('GET', `/strategies/${id}`, { signal });
}

// Edita la estrategia creando su versión siguiente. La opción solo rige si es la activa; sin
// opción, el backend aplica PROXIMA_SEMANA.
export function createVersion(
  id: number,
  input: StrategyInput,
  requote?: RequoteTiming,
  signal?: AbortSignal,
): Promise<StrategyChange> {
  return request<StrategyChange>('POST', `/strategies/${id}/versions`, { body: { ...input, requote }, signal });
}

// Deja activa la versión más reciente de la estrategia
export function activateStrategy(id: number, requote?: RequoteTiming, signal?: AbortSignal): Promise<StrategyChange> {
  return request<StrategyChange>('POST', `/strategies/${id}/activate`, { body: { requote }, signal });
}
