import { describe, expect, it } from 'vitest';
import { METRICS, PENDING_REASONS, RECALCULATION_ORIGINS } from '../../src/types/quotes';
import {
  isLowerBetter,
  metricLabel,
  pendingReasonLabel,
  recalculationOriginLabel,
} from '../../src/utils/metrics';

describe('metrics', () => {
  it('hay 19 métricas y cada una tiene su propia etiqueta en español', () => {
    const labels = METRICS.map((metric) => metricLabel(metric));

    expect(METRICS).toHaveLength(19);
    labels.forEach((label) => expect(label).toMatch(/\S/));
    expect(new Set(labels).size).toBe(METRICS.length);
  });

  it('usa los nombres de la consigna para las métricas', () => {
    expect(metricLabel('GOALS')).toBe('goles');
    expect(metricLabel('TACKLES')).toBe('quites');
    expect(metricLabel('SUCCESSFUL_DRIBBLES')).toBe('regates');
    expect(metricLabel('SHOTS')).toBe('tiros');
    expect(metricLabel('CLEAN_SHEETS')).toBe('valla invicta');
  });

  it('ninguna etiqueta queda con el nombre en inglés', () => {
    METRICS.forEach((metric) => expect(metricLabel(metric)).not.toContain(metric));
  });

  it('solo faltas y tarjetas son de "menos es mejor"', () => {
    const lowerIsBetter = METRICS.filter((metric) => isLowerBetter(metric));

    expect(lowerIsBetter).toEqual(['FOULS_COMMITTED', 'YELLOW_CARDS', 'RED_CARDS']);
  });

  it('traduce los motivos de una semana pendiente', () => {
    expect(pendingReasonLabel('MISSING_STATS')).toMatch(/^faltan estadísticas/);
    expect(pendingReasonLabel('PREVIOUS_WEEK_PENDING')).toBe('hay una semana anterior pendiente');
    PENDING_REASONS.forEach((reason) => expect(pendingReasonLabel(reason)).not.toContain(reason));
  });

  it('traduce el origen de cada recálculo', () => {
    const labels = RECALCULATION_ORIGINS.map((origin) => recalculationOriginLabel(origin));

    expect(labels).toEqual([
      'cierre semanal de los lunes',
      'arranque de la aplicación',
      'disparo manual',
      'cambio de estrategia',
    ]);
  });
});
