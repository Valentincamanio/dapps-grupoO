// Historia 2 de la 008: la pantalla Portfolio con el portfolio actual. Las posiciones y los totales
// salen del backend simulado; la ganancia o pérdida se lee con signo y texto, sin depender del color.
import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Portfolio } from '../../src/types/market';
import { CURRENT_PORTFOLIO, MARKET_USER_ID } from '../mocks/market';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const PORTFOLIO_PATH = '*/api/users/:id/portfolio';
const [FIRST, SECOND] = CURRENT_PORTFOLIO.positions;

const EMPTY_PORTFOLIO: Portfolio = {
  userId: MARKET_USER_ID,
  date: null,
  positions: [],
  totals: {
    balance: 1000,
    availableBalance: 1000,
    reservedBalance: 0,
    invested: 0,
    value: 0,
    profit: 0,
    realizedProfit: 0,
    equity: 1000,
  },
};

function renderPortfolio() {
  return renderWithRouter({ initialEntries: ['/portfolio'], session: 'valid' });
}

function rowOf(playerName: string): HTMLElement {
  const row = screen.getByRole('rowheader', { name: new RegExp(playerName) }).closest('tr');
  if (!row) throw new Error(`No hay una fila para ${playerName}`);
  return row;
}

// El valor que acompaña a una etiqueta de los totales
function totalOf(label: string): HTMLElement {
  const value = screen.getByText(label, { selector: 'dt' }).nextElementSibling;
  if (!(value instanceof HTMLElement)) throw new Error(`No hay un total ${label}`);
  return value;
}

describe('PortfolioPage', () => {
  it('muestra las posiciones y los totales del portfolio actual', async () => {
    renderPortfolio();

    expect(await screen.findByRole('heading', { name: 'Portfolio' })).toBeInTheDocument();
    await screen.findByRole('table');
    expect(screen.getAllByRole('rowheader')).toHaveLength(2);
    expect(rowOf(FIRST.player.name)).toHaveTextContent('10,30 cr.');
    expect(rowOf(SECOND.player.name)).toHaveTextContent('1,0100 cr.');
    expect(totalOf('Saldo libre')).toHaveTextContent('993,82 cr.');
    expect(totalOf('Saldo reservado')).toHaveTextContent('6,18 cr.');
    expect(totalOf('Ganancia realizada')).toHaveTextContent('+0,20 gana');
    expect(totalOf('Patrimonio')).toHaveTextContent('1.020,60 cr.');
  });

  it('la ganancia o la pérdida de cada posición se lee con el signo y el texto', async () => {
    const losing: Portfolio = {
      ...CURRENT_PORTFOLIO,
      positions: [FIRST, { ...SECOND, value: 9.1, profit: -1, profitPercent: -9.9 }],
    };
    server.use(http.get(PORTFOLIO_PATH, () => HttpResponse.json(losing)));

    renderPortfolio();

    await screen.findByRole('table');
    expect(within(rowOf(FIRST.player.name)).getByText('gana')).toBeInTheDocument();
    expect(rowOf(FIRST.player.name)).toHaveTextContent('+0,30');
    expect(within(rowOf(SECOND.player.name)).getByText('pierde')).toBeInTheDocument();
    expect(rowOf(SECOND.player.name)).toHaveTextContent('-1,00');
    expect(rowOf(SECOND.player.name)).toHaveTextContent('-9,90 %');
  });

  it('sin tenencias muestra la lista vacía y el saldo como patrimonio', async () => {
    server.use(http.get(PORTFOLIO_PATH, () => HttpResponse.json(EMPTY_PORTFOLIO)));

    renderPortfolio();

    expect(await screen.findByText('todavía no tenés tokens de ningún jugador.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
    expect(totalOf('Patrimonio')).toHaveTextContent('1.000,00 cr.');
  });

  it('si no se puede cargar avisa en español y reintentar lo recupera', async () => {
    server.use(http.get(PORTFOLIO_PATH, () => HttpResponse.error(), { once: true }));
    const { user } = renderPortfolio();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('no se pudo cargar el portfolio');
    expect(screen.queryByRole('table')).toBeNull();

    await user.click(within(alert).getByRole('button', { name: 'reintentar' }));

    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('la barra ofrece el enlace al portfolio al usuario común', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'], session: 'valid' });

    expect(await screen.findByRole('link', { name: 'Portfolio' })).toHaveAttribute('href', '/portfolio');
  });

  it('sin sesión lleva a entrar', async () => {
    const { router } = renderWithRouter({ initialEntries: ['/portfolio'] });

    await screen.findByRole('button', { name: /entrar/i });
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.queryByRole('link', { name: 'Portfolio' })).toBeNull();
  });
});
