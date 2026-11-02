// Historia 2 de la 008: los totales del portfolio (FR-038). El libre y el reservado solo se muestran
// si vienen, porque el portfolio a una fecha no los informa (FR-040).
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PortfolioTotals } from '../../src/components/PortfolioTotals';
import { CURRENT_PORTFOLIO, PORTFOLIO_AT_DATE } from '../mocks/market';

// El valor que acompaña a una etiqueta de la lista
function valueOf(label: string): HTMLElement {
  const term = screen.getByText(label, { selector: 'dt' });
  const value = term.nextElementSibling;
  if (!(value instanceof HTMLElement)) throw new Error(`No hay un valor para ${label}`);
  return value;
}

describe('PortfolioTotals', () => {
  it('muestra libre, reservado, saldo, invertido, valor, ganancia, realizada y patrimonio', () => {
    render(<PortfolioTotals totals={CURRENT_PORTFOLIO.totals} />);

    expect(screen.getByRole('heading', { name: 'Totales' })).toBeInTheDocument();
    expect(valueOf('Saldo libre')).toHaveTextContent('993,82 cr.');
    expect(valueOf('Saldo reservado')).toHaveTextContent('6,18 cr.');
    expect(valueOf('Saldo total')).toHaveTextContent('1.000,00 cr.');
    expect(valueOf('Invertido')).toHaveTextContent('20,10 cr.');
    expect(valueOf('Valor de las posiciones')).toHaveTextContent('20,60 cr.');
    expect(valueOf('Ganancia o pérdida')).toHaveTextContent('+0,50 gana');
    expect(valueOf('Ganancia realizada')).toHaveTextContent('+0,20 gana');
    expect(valueOf('Patrimonio')).toHaveTextContent('1.020,60 cr.');
  });

  it('sin libre ni reservado no los muestra', () => {
    render(<PortfolioTotals totals={PORTFOLIO_AT_DATE.totals} />);

    expect(screen.queryByText('Saldo libre')).toBeNull();
    expect(screen.queryByText('Saldo reservado')).toBeNull();
    expect(valueOf('Saldo total')).toHaveTextContent('990,00 cr.');
    expect(valueOf('Patrimonio')).toHaveTextContent('1.000,30 cr.');
  });

  it('una pérdida se lee con el signo y el texto', () => {
    render(<PortfolioTotals totals={{ ...CURRENT_PORTFOLIO.totals, profit: -0.5, realizedProfit: -0.2 }} />);

    expect(valueOf('Ganancia o pérdida')).toHaveTextContent('-0,50 pierde');
    expect(valueOf('Ganancia realizada')).toHaveTextContent('-0,20 pierde');
  });

  it('sin ganancia realizada, como en el portfolio del superusuario, muestra un guion', () => {
    render(<PortfolioTotals totals={{ ...CURRENT_PORTFOLIO.totals, realizedProfit: null }} />);

    expect(valueOf('Ganancia realizada')).toHaveTextContent('sin datos');
  });
});
