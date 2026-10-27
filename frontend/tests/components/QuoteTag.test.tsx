import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { QuoteTag } from '../../src/components/QuoteTag';
import type { Quote, QuoteBadge } from '../../src/types/quotes';
import { STRATEGY_A } from '../mocks/quotes';

const QUOTE: Quote = {
  value: 103,
  tokenPrice: 1.03,
  variation: { amount: 3, percent: 3 },
  weekStart: '2026-08-17',
  validFrom: '2026-08-24T03:00:00Z',
  type: 'CALCULATED',
  strategy: STRATEGY_A,
};

describe('QuoteTag', () => {
  it('lista: muestra la cotización en créditos y la variación con signo y texto', () => {
    const { container } = render(<QuoteTag badge={{ status: 'ready', quote: QUOTE }} />);

    expect(screen.getByText('103,00 cr.')).toBeInTheDocument();
    expect(screen.getByText('+3,00 (+3,00 %)')).toBeInTheDocument();
    expect(screen.getByText('sube')).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/[$€]|USD|EUR/);
  });

  it('la cotización inicial se muestra sin variación', () => {
    const initial: Quote = { ...QUOTE, type: 'INITIAL', variation: undefined };
    render(<QuoteTag badge={{ status: 'ready', quote: initial }} />);

    expect(screen.getByText('103,00 cr.')).toBeInTheDocument();
    expect(screen.queryByText(/sube|baja/)).toBeNull();
  });

  it('un jugador sin cotización dice "sin cotización" sin inventar un valor', () => {
    const { container } = render(<QuoteTag badge={{ status: 'none' }} />);

    expect(screen.getByText('sin cotización')).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/\d/);
  });

  it('muestra los estados de carga y de error', () => {
    const { rerender } = render(<QuoteTag badge={{ status: 'loading' }} />);
    expect(screen.getByText('cargando cotización...')).toBeInTheDocument();

    rerender(<QuoteTag badge={{ status: 'error' }} />);

    expect(screen.getByText('cotización no disponible')).toBeInTheDocument();
  });

  it.each<QuoteBadge>([
    { status: 'loading' },
    { status: 'none' },
    { status: 'error' },
    { status: 'ready', quote: QUOTE },
  ])('en el estado $status no usa listas, botones ni enlaces', (badge) => {
    const { container } = render(<QuoteTag badge={badge} />);

    expect(container.querySelector('ul, ol')).toBeNull();
    expect(screen.queryAllByRole('button')).toHaveLength(0);
    expect(screen.queryAllByRole('link')).toHaveLength(0);
  });
});
