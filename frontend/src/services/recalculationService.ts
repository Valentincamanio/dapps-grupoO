// Recálculo manual y simulación (specs/007-cotizacion-estrategias/contracts/quotes-admin-api.yaml).
// Solo ADMIN: sin sesión responde 401 y con un usuario común, 403.
import type {
  RecalculationReport,
  SimulationPage,
  SimulationQuery,
  SimulationRequest,
} from '../types/quotes';
import { request } from './httpClient';

// Espera a que termine y devuelve el informe; 409 si hay otro recálculo o una sincronización
export function recalculate(signal?: AbortSignal): Promise<RecalculationReport> {
  return request<RecalculationReport>('POST', '/quotes/recalculate', { signal });
}

// Simula sin guardar nada. Los filtros y la hoja van en la dirección; los vacíos no viajan.
export function simulate(
  simulation: SimulationRequest,
  query: SimulationQuery = {},
  signal?: AbortSignal,
): Promise<SimulationPage> {
  const team = query.team?.trim();
  return request<SimulationPage>('POST', '/quotes/simulate', {
    query: { ...query, team: team ? team : undefined },
    body: simulation,
    signal,
  });
}
