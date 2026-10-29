// Historia 2 de la 008: la pantalla Portfolio con el portfolio actual. Las posiciones y los totales
// salen del backend simulado; la ganancia o pérdida se lee con signo y texto, sin depender del color.
import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Portfolio } from '../../src/types/market';
import { CURRENT_PORTFOLIO, MARKET_USER_ID, PORTFOLIO_AT_DATE, PORTFOLIO_DATE } from '../mocks/market';
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

  it('elegir una fecha la escribe en la dirección y muestra el portfolio de ese día', async () => {
    const { user, router } = renderPortfolio();
    await screen.findByRole('table');

    await user.type(screen.getByLabelText('Ver el portfolio de una fecha'), PORTFOLIO_DATE);

    await waitFor(() => expect(router.state.location.search).toBe(`?date=${PORTFOLIO_DATE}`));
    expect(await screen.findByRole('table', { name: /^Tus posiciones al 25\/08\/2026/ })).toBeInTheDocument();
    expect(screen.getAllByRole('rowheader')).toHaveLength(PORTFOLIO_AT_DATE.positions.length);
    expect(totalOf('Saldo total')).toHaveTextContent('990,00 cr.');
    expect(totalOf('Patrimonio')).toHaveTextContent('1.000,30 cr.');
  });

  it('a una fecha no muestra saldo libre, reservado ni comprometidos', async () => {
    renderWithRouter({ initialEntries: [`/portfolio?date=${PORTFOLIO_DATE}`], session: 'valid' });

    await screen.findByRole('table', { name: /^Tus posiciones al 25\/08\/2026/ });
    expect(screen.getByRole('heading', { name: 'Totales al 25/08/2026' })).toBeInTheDocument();
    expect(screen.queryByText('Saldo libre', { selector: 'dt' })).toBeNull();
    expect(screen.queryByText('Saldo reservado', { selector: 'dt' })).toBeNull();
    const [row] = screen.getAllByRole('row').slice(1);
    expect(row.querySelector('[data-label="Comprometidos"]')).toHaveTextContent('—');
    expect(screen.getByLabelText('Ver el portfolio de una fecha')).toHaveValue(PORTFOLIO_DATE);
  });

  it('volver al actual saca la fecha de la dirección', async () => {
    const { user, router } = renderWithRouter({
      initialEntries: [`/portfolio?date=${PORTFOLIO_DATE}`],
      session: 'valid',
    });
    await screen.findByRole('table', { name: /^Tus posiciones al/ });

    await user.click(screen.getByRole('button', { name: 'volver al actual' }));

    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(await screen.findByRole('table', { name: 'Tus posiciones, valuadas con la cotización vigente' }))
      .toBeInTheDocument();
    expect(totalOf('Saldo libre')).toHaveTextContent('993,82 cr.');
    expect(screen.getByLabelText('Ver el portfolio de una fecha')).toHaveValue('');
    expect(screen.queryByRole('button', { name: 'volver al actual' })).toBeNull();
  });

  it('una fecha que el servidor rechaza se explica en español y se puede volver al actual', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/portfolio?date=2026-08-10'], session: 'valid' });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('La fecha 2026-08-10 es anterior al registro del usuario (2026-08-17).');
    expect(screen.queryByRole('table')).toBeNull();

    await user.click(within(alert).getByRole('button', { name: 'volver al actual' }));

    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(await screen.findByRole('table')).toBeInTheDocument();
  });

  it('una fecha mal escrita en la dirección muestra el mensaje del servidor', async () => {
    renderWithRouter({ initialEntries: ['/portfolio?date=2026-13-01'], session: 'valid' });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "La fecha '2026-13-01' no es válida: use el formato AAAA-MM-DD.",
    );
  });

  it('sin sesión lleva a entrar', async () => {
    const { router } = renderWithRouter({ initialEntries: ['/portfolio'] });

    await screen.findByRole('button', { name: /entrar/i });
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.queryByRole('link', { name: 'Portfolio' })).toBeNull();
  });
});
