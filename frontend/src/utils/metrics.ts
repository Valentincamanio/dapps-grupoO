// Etiquetas en español de las métricas, de los motivos de semana pendiente y de los orígenes del
// recálculo (research D5 y D24). La API usa los nombres en inglés tal cual el backend.
import type { Metric, PendingReason, RecalculationOrigin } from '../types/quotes';

const METRIC_LABELS: Record<Metric, string> = {
  GOALS: 'goles',
  ASSISTS: 'asistencias',
  SHOTS: 'tiros',
  SHOTS_ON_TARGET: 'tiros al arco',
  KEY_PASSES: 'pases clave',
  SUCCESSFUL_DRIBBLES: 'regates',
  TACKLES: 'quites',
  INTERCEPTIONS: 'intercepciones',
  CLEARANCES: 'despejes',
  AERIALS_WON: 'duelos aéreos ganados',
  PASSES: 'pases',
  MINUTES_PLAYED: 'minutos jugados',
  RATING: 'calificación del partido',
  PASS_ACCURACY: 'precisión de pases',
  TEAM_RESULT: 'resultado del equipo',
  CLEAN_SHEETS: 'valla invicta',
  FOULS_COMMITTED: 'faltas cometidas',
  YELLOW_CARDS: 'tarjetas amarillas',
  RED_CARDS: 'tarjetas rojas',
};

// Las de "menos es mejor": se invierten al compararlas
const LOWER_IS_BETTER: ReadonlySet<Metric> = new Set<Metric>(['FOULS_COMMITTED', 'YELLOW_CARDS', 'RED_CARDS']);

const PENDING_REASON_LABELS: Record<PendingReason, string> = {
  MISSING_STATS: 'faltan estadísticas de partidos terminados y todavía no pasaron 7 días desde el cierre',
  PREVIOUS_WEEK_PENDING: 'hay una semana anterior pendiente',
};

const ORIGIN_LABELS: Record<RecalculationOrigin, string> = {
  WEEKLY: 'cierre semanal de los lunes',
  STARTUP: 'arranque de la aplicación',
  MANUAL: 'disparo manual',
  STRATEGY_CHANGE: 'cambio de estrategia',
};

export function metricLabel(metric: Metric): string {
  return METRIC_LABELS[metric];
}

export function isLowerBetter(metric: Metric): boolean {
  return LOWER_IS_BETTER.has(metric);
}

export function pendingReasonLabel(reason: PendingReason): string {
  return PENDING_REASON_LABELS[reason];
}

export function recalculationOriginLabel(origin: RecalculationOrigin): string {
  return ORIGIN_LABELS[origin];
}
