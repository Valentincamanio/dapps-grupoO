import { describe, expect, it } from 'vitest';
import type { ChartPoint, Quote } from '../../src/types/quotes';
import {
  DEFAULT_MARGINS,
  axisTicks,
  polylinePoints,
  scalePoints,
  toChartPoints,
  valueDomain,
} from '../../src/utils/chart';

const STRATEGY = { id: 1, name: 'Rendimiento de partido', version: 1 };

function quote(weekStart: string, value: number, validFrom = `${weekStart}T03:00:00Z`): Quote {
  return { value, tokenPrice: value / 100, weekStart, validFrom, type: 'CALCULATED', strategy: STRATEGY };
}

function point(weekStart: string, value: number): ChartPoint {
  return { week: weekStart, value, quote: quote(weekStart, value) };
}

const TOP = DEFAULT_MARGINS.top;
const BOTTOM = 100 - DEFAULT_MARGINS.bottom;
const LEFT = DEFAULT_MARGINS.left;
const RIGHT = 100 - DEFAULT_MARGINS.right;

describe('chart', () => {
  it('un solo punto queda centrado y sin dividir por cero', () => {
    const [scaled] = scalePoints([point('2026-08-17', 100)]);

    expect(scaled.x).toBeCloseTo((LEFT + RIGHT) / 2);
    expect(scaled.y).toBeCloseTo((TOP + BOTTOM) / 2);
    expect(Number.isFinite(scaled.y)).toBe(true);
  });

  it('con valores iguales el dominio se abre alrededor del valor', () => {
    const domain = valueDomain([100, 100, 100]);

    expect(domain.min).toBeLessThan(100);
    expect(domain.max).toBeGreaterThan(100);
  });

  it('con valores iguales todos los puntos quedan a la misma altura, en el medio', () => {
    const points = [point('2026-08-17', 50), point('2026-08-24', 50), point('2026-08-31', 50)];

    const scaled = scalePoints(points);

    scaled.forEach((item) => expect(item.y).toBeCloseTo((TOP + BOTTOM) / 2));
  });

  it('con varios puntos reparte x entre los márgenes y el valor más alto queda arriba', () => {
    const points = [point('2026-08-17', 100), point('2026-08-24', 110), point('2026-08-31', 90)];

    const scaled = scalePoints(points);

    expect(scaled.map((item) => item.x)).toEqual([LEFT, (LEFT + RIGHT) / 2, RIGHT]);
    expect(scaled[1].y).toBeCloseTo(TOP);
    expect(scaled[2].y).toBeCloseTo(BOTTOM);
    expect(scaled[0].y).toBeCloseTo((TOP + BOTTOM) / 2);
  });

  it('los ticks del eje van del mínimo al máximo del dominio', () => {
    const ticks = axisTicks({ min: 90, max: 120 }, 4);

    expect(ticks.map((tick) => tick.value)).toEqual([90, 100, 110, 120]);
    expect(ticks[0].y).toBeCloseTo(BOTTOM);
    expect(ticks[3].y).toBeCloseTo(TOP);
  });

  it('arma el atributo points del polyline', () => {
    const scaled = scalePoints([point('2026-08-17', 100), point('2026-08-24', 110)]);

    const text = polylinePoints(scaled);

    expect(text).toBe(`${LEFT},${BOTTOM} ${RIGHT},${TOP}`);
  });

  it('toChartPoints deja un punto por semana con la última cotización', () => {
    const quotes = [
      quote('2026-08-17', 103, '2026-08-24T03:00:00Z'),
      quote('2026-08-17', 104, '2026-08-26T15:30:00Z'),
      quote('2026-08-24', 101, '2026-08-31T03:00:00Z'),
    ];

    const points = toChartPoints(quotes);

    expect(points.map((item) => [item.week, item.value])).toEqual([
      ['2026-08-17', 104],
      ['2026-08-24', 101],
    ]);
  });

  it('sin puntos no se rompe', () => {
    expect(scalePoints([])).toEqual([]);
    expect(polylinePoints([])).toBe('');
  });
});
