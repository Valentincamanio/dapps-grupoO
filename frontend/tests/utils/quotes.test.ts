import { describe, expect, it } from 'vitest';
import type { Quote } from '../../src/types/quotes';
import {
  formatCredits,
  formatDay,
  formatTokenPrice,
  formatVariation,
  formatVariationAmount,
  lastPerWeek,
  quoteTypeLabel,
  variationDirection,
  variationText,
  weekLabel,
} from '../../src/utils/quotes';

const STRATEGY = { id: 1, name: 'Rendimiento de partido', version: 1 };

function quote(overrides: Partial<Quote>): Quote {
  return {
    value: 100,
    tokenPrice: 1,
    weekStart: '2026-08-17',
    validFrom: '2026-08-17T03:00:00Z',
    type: 'INITIAL',
    strategy: STRATEGY,
    ...overrides,
  };
}

const CURRENCY_SYMBOLS = /[$€]|USD|EUR/;

describe('quotes', () => {
  it('formatea los créditos con dos decimales y coma', () => {
    const text = formatCredits(103);

    expect(text).toBe('103,00 cr.');
  });

  it('formatea los créditos con separador de miles', () => {
    const text = formatCredits(1234.5);

    expect(text).toBe('1.234,50 cr.');
  });

  it('formatea el precio por token con cuatro decimales', () => {
    const text = formatTokenPrice(1.03);

    expect(text).toBe('1,0300 cr.');
  });

  it('ningún importe lleva símbolo de moneda', () => {
    const texts = [
      formatCredits(103),
      formatTokenPrice(1.03),
      formatVariation({ amount: -2, percent: -1.94 }),
    ];

    texts.forEach((text) => expect(text).not.toMatch(CURRENCY_SYMBOLS));
  });

  it('una variación positiva lleva signo más y dice "sube"', () => {
    const text = formatVariation({ amount: 3, percent: 3 });

    expect(text).toBe('+3,00 (+3,00 %) sube');
  });

  it('una variación negativa lleva signo menos y dice "baja"', () => {
    const variation = { amount: -2, percent: -1.94 };

    const text = formatVariation(variation);

    expect(text).toBe('-2,00 (-1,94 %) baja');
    expect(variationDirection(variation)).toBe('down');
  });

  it('una variación nula no lleva signo y dice "sin cambios"', () => {
    const variation = { amount: 0, percent: 0 };

    expect(formatVariationAmount(variation)).toBe('0,00 (0,00 %)');
    expect(variationText(variation)).toBe('sin cambios');
    expect(variationDirection(variation)).toBe('flat');
  });

  it('traduce el tipo de cotización', () => {
    const labels = [quoteTypeLabel('INITIAL'), quoteTypeLabel('CALCULATED'), quoteTypeLabel('REQUOTED')];

    expect(labels).toEqual(['inicial', 'calculada', 'recotizada']);
  });

  it('arma la etiqueta de la semana sin correr el día por la zona horaria', () => {
    expect(formatDay('2026-08-17')).toBe('17/08/2026');
    expect(weekLabel('2026-08-17')).toBe('semana del 17/08/2026');
  });

  it('lastPerWeek deja una cotización por semana ordenadas por semana', () => {
    const quotes = [
      quote({ weekStart: '2026-08-24', validFrom: '2026-08-31T03:00:00Z', type: 'CALCULATED', value: 101 }),
      quote({ weekStart: '2026-08-17', validFrom: '2026-08-24T03:00:00Z', type: 'CALCULATED', value: 103 }),
    ];

    const result = lastPerWeek(quotes);

    expect(result.map((item) => item.weekStart)).toEqual(['2026-08-17', '2026-08-24']);
  });

  it('lastPerWeek muestra la recotizada en una semana recotizada', () => {
    const quotes = [
      quote({}),
      quote({ validFrom: '2026-08-24T03:00:00Z', type: 'CALCULATED', value: 103 }),
      quote({ validFrom: '2026-08-26T15:30:00Z', type: 'REQUOTED', value: 104 }),
      quote({ weekStart: '2026-08-24', validFrom: '2026-08-31T03:00:00Z', type: 'CALCULATED', value: 101 }),
    ];

    const result = lastPerWeek(quotes);

    expect(result).toHaveLength(2);
    expect(result[0]).toMatchObject({ type: 'REQUOTED', value: 104 });
    expect(result[1]).toMatchObject({ type: 'CALCULATED', value: 101 });
  });

  it('lastPerWeek no depende del orden de llegada', () => {
    const quotes = [
      quote({ validFrom: '2026-08-26T15:30:00Z', type: 'REQUOTED', value: 104 }),
      quote({ validFrom: '2026-08-24T03:00:00Z', type: 'CALCULATED', value: 103 }),
    ];

    const result = lastPerWeek(quotes);

    expect(result).toEqual([quotes[0]]);
  });

  it('lastPerWeek sin cotizaciones devuelve una lista vacía', () => {
    expect(lastPerWeek([])).toEqual([]);
  });
});
