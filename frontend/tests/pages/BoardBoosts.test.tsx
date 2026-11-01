// Los potenciados de la semana arriba de la pizarra: los ve cualquiera, con su multiplicador, su
// cotización actual y cuánto falta para el cierre; sin potenciados no se muestra nada.
import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { formatCredits } from '../../src/utils/quotes';
import { BOOST_CURRENT_MONDAY, setMockBoosts } from '../mocks/boostHandlers';
import { PLAYERS } from '../mocks/players';
import { currentQuoteOf } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const [FIRST, SECOND, THIRD, FOURTH] = PLAYERS;

function boostFour() {
  setMockBoosts(BOOST_CURRENT_MONDAY, [
    { playerId: FIRST.id, percent: 300 },
    { playerId: SECOND.id, percent: 400 },
    { playerId: THIRD.id, percent: 250 },
    { playerId: FOURTH.id, percent: 200 },
  ]);
}

async function boostedSection(): Promise<HTMLElement> {
  return screen.findByRole('region', { name: 'Potenciados de la semana' });
}

describe('potenciados en la pizarra', () => {
  it('sin potenciados la pizarra no muestra la sección', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'] });

    await screen.findByText(PLAYERS[0].name);

    expect(screen.queryByRole('region', { name: 'Potenciados de la semana' })).toBeNull();
    expect(screen.queryByText('Todos los jugadores')).toBeNull();
  });

  it('un visitante ve los cuatro, en orden, con su multiplicador y su volatilidad', async () => {
    boostFour();

    renderWithRouter({ initialEntries: ['/pizarra'] });
    const section = await boostedSection();

    const cards = within(section).getAllByRole('link');
    expect(cards.map((card) => card.getAttribute('href'))).toEqual(
      [FIRST, SECOND, THIRD, FOURTH].map((player) => `/jugadores/${player.id}`),
    );
    expect(within(cards[0]).getByText('×3')).toBeInTheDocument();
    expect(within(cards[2]).getByText('×2,5')).toBeInTheDocument();
    expect(within(cards[1]).getByText('400 %')).toBeInTheDocument();
    expect(within(section).getByText('26 oct – 1 nov')).toBeInTheDocument();
    expect(screen.getByText('Todos los jugadores')).toBeInTheDocument();
  });

  it('cada tarjeta muestra la cotización actual, que todavía no lleva el potenciado', async () => {
    boostFour();

    renderWithRouter({ initialEntries: ['/pizarra'] });
    const section = await boostedSection();

    const first = within(section).getAllByRole('link')[0];
    expect(await within(first).findByText('cotización actual')).toBeInTheDocument();
    expect(within(first).getByText(formatCredits(currentQuoteOf(FIRST.id)!.value))).toBeInTheDocument();
  });

  it('muestra cuánto falta para el cierre y explica qué es un potenciado', async () => {
    boostFour();

    renderWithRouter({ initialEntries: ['/pizarra'] });
    const section = await boostedSection();

    expect(within(section).getByText(/^cierra en 1d 11h \d+m$/)).toBeInTheDocument();
    expect(within(section).getByRole('button', { name: 'qué es un potenciado' })).toHaveAccessibleDescription(
      /En el cierre del domingo, su factor de escala y su volatilidad se multiplican/,
    );
  });

  it('un usuario y el administrador también los ven', async () => {
    boostFour();

    renderWithRouter({ initialEntries: ['/pizarra'], session: 'valid' });

    expect(within(await boostedSection()).getAllByRole('link')).toHaveLength(4);
  });

  it('si no se pueden leer los potenciados, la pizarra sigue igual', async () => {
    server.use(http.get('*/api/players/boosts/current', () => HttpResponse.json({}, { status: 500 })));

    renderWithRouter({ initialEntries: ['/pizarra'] });

    expect(await screen.findByText(PLAYERS[0].name)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Potenciados de la semana' })).toBeNull();
  });
});
