import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { QuotePanel } from '../../src/components/QuotePanel';
import type { Quote } from '../../src/types/quotes';
import { STRATEGY_B } from '../mocks/quotes';

const REQUOTED: Quote = {
  value: 104,
  tokenPrice: 1.04,
  variation: { amount: 4, percent: 4 },
  weekStart: '2026-08-17',
  validFrom: '2026-08-26T15:30:00Z',
  type: 'REQUOTED',
  strategy: STRATEGY_B,
};

// El <dd> que sigue a la etiqueta de texto
function valueOf(label: string): string {
  return screen.getByText(label).nextElementSibling?.textContent ?? '';
}

describe('QuotePanel', () => {
  it('muestra el valor del jugador y el precio por token con etiquetas de texto', () => {
    render(<QuotePanel quote={REQUOTED} />);

    expect(valueOf('Valor del jugador')).toBe('104,00 cr.');
    expect(valueOf('Precio por token')).toBe('1,0400 cr.');
  });

  it('muestra la variación respecto de la semana anterior con signo y texto', () => {
    render(<QuotePanel quote={REQUOTED} />);

    expect(valueOf('Variación respecto de la semana anterior')).toBe('▲+4,00 (+4,00 %)sube');
  });

  it('muestra el tipo, la semana, la estrategia y la versión', () => {
    render(<QuotePanel quote={REQUOTED} />);

    expect(valueOf('Cotización')).toBe('recotizada, semana del 17/08/2026');
    expect(valueOf('Estrategia')).toBe(`${STRATEGY_B.name}, versión ${STRATEGY_B.version}`);
  });

  it('la cotización inicial aclara que no tiene variación', () => {
    render(<QuotePanel quote={{ ...REQUOTED, type: 'INITIAL', variation: undefined }} />);

    expect(valueOf('Variación respecto de la semana anterior')).toBe(
      'sin variación: es la cotización inicial',
    );
  });

  it('no tiene botones ni enlaces y no usa símbolos de moneda', () => {
    const { container } = render(<QuotePanel quote={REQUOTED} />);

    expect(screen.queryAllByRole('button')).toHaveLength(0);
    expect(screen.queryAllByRole('link')).toHaveLength(0);
    expect(container.textContent).not.toMatch(/[$€]|USD|EUR/);
  });
});
