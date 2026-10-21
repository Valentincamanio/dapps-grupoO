// Historia 3 de la 007: la cotización y la evolución en la ficha del jugador (FR-060, FR-061).
import { screen, waitFor, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { EMPTY_RANGE_TEXT } from '../../src/components/QuoteChart';
import { leagueLabel, positionLabel } from '../../src/utils/positions';
import {
  formatCredits,
  formatTokenPrice,
  formatVariationAmount,
  variationText,
} from '../../src/utils/quotes';
import { PLAYERS } from '../mocks/players';
import { currentQuoteOf, PLAYER_WITHOUT_QUOTE_ID, REQUOTED_PLAYER_ID, WEEKS } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const PLAYER = PLAYERS.find((player) => player.id === REQUOTED_PLAYER_ID)!;
const PLAYER_WITHOUT_QUOTE = PLAYERS.find((player) => player.id === PLAYER_WITHOUT_QUOTE_ID)!;

function pointButtons(): HTMLElement[] {
  return screen.queryAllByRole('button', { name: /^Cotización de la semana del / });
}

// El <dd> que sigue a la etiqueta de texto
function valueOf(label: string): string {
  return screen.getByText(label).nextElementSibling?.textContent ?? '';
}

function openPlayer(id = PLAYER.id) {
  return renderWithRouter({ initialEntries: [`/jugadores/${id}`] });
}

describe('PlayerPage - cotización', () => {
  it('muestra el valor del jugador, el precio por token y la variación semanal', async () => {
    openPlayer();
    const quote = currentQuoteOf(PLAYER.id)!;

    await screen.findByText('Valor del jugador');

    expect(valueOf('Valor del jugador')).toBe(formatCredits(quote.value));
    expect(valueOf('Precio por token')).toBe(formatTokenPrice(quote.tokenPrice));
    const variation = valueOf('Variación respecto de la semana anterior');
    expect(variation).toContain(formatVariationAmount(quote.variation!));
    expect(variation).toContain(variationText(quote.variation!));
  });

  it('los textos del catálogo no se repiten en el panel de cotización', async () => {
    openPlayer();
    await screen.findByText('Valor del jugador');
    await waitFor(() => expect(pointButtons().length).toBeGreaterThan(0));

    expect(screen.getAllByText(PLAYER.team)).toHaveLength(1);
    expect(screen.getAllByText(positionLabel(PLAYER.position))).toHaveLength(1);
    expect(screen.getAllByText(leagueLabel(PLAYER.league))).toHaveLength(1);
  });

  it('un jugador sin cotización lo indica y los datos del catálogo siguen visibles', async () => {
    openPlayer(PLAYER_WITHOUT_QUOTE.id);

    expect(await screen.findByText(/^sin cotización/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: PLAYER_WITHOUT_QUOTE.name })).toBeInTheDocument();
    expect(screen.getByText(PLAYER_WITHOUT_QUOTE.team)).toBeInTheDocument();
    expect(screen.getByText(positionLabel(PLAYER_WITHOUT_QUOTE.position))).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.queryByText('Valor del jugador')).toBeNull();
  });

  it('mientras carga lo indica y un error de la cotización ofrece reintentar sin perder la ficha', async () => {
    server.use(
      http.get('*/api/players/:id/quotes/current', async () => {
        await delay(50);
        return HttpResponse.error();
      }, { once: true }),
    );
    const { user } = openPlayer();

    expect(await screen.findByText('cargando la cotización...')).toBeInTheDocument();
    const retry = await screen.findByRole('button', { name: 'reintentar la cotización' });
    expect(screen.getByRole('heading', { name: PLAYER.name })).toBeInTheDocument();

    await user.click(retry);

    expect(await screen.findByText('Valor del jugador')).toBeInTheDocument();
  });
});

describe('PlayerPage - evolución', () => {
  it('el gráfico muestra un punto por semana y una tabla con los mismos puntos', async () => {
    const { user } = openPlayer();

    await waitFor(() => expect(pointButtons()).toHaveLength(WEEKS.length));

    await user.click(screen.getByText('ver la tabla de cotizaciones por semana'));
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(WEEKS.length);
  });

  it('elegir un rango recorta el gráfico a las semanas de ese rango', async () => {
    const { user } = openPlayer();
    await waitFor(() => expect(pointButtons()).toHaveLength(WEEKS.length));

    await user.type(screen.getByLabelText('Desde'), '2026-09-01');
    await user.type(screen.getByLabelText('Hasta'), '2026-09-20');

    // Rigen dentro del rango las calculadas de las semanas del 31/08 y del 07/09
    await waitFor(() =>
      expect(pointButtons().map((button) => button.getAttribute('aria-label'))).toEqual([
        'Cotización de la semana del 31/08/2026',
        'Cotización de la semana del 07/09/2026',
      ]),
    );
  });

  it('un rango sin cotizaciones muestra el mensaje y la ficha no se rompe', async () => {
    const { user } = openPlayer();
    await waitFor(() => expect(pointButtons()).toHaveLength(WEEKS.length));

    await user.type(screen.getByLabelText('Desde'), '2025-01-01');
    await user.type(screen.getByLabelText('Hasta'), '2025-01-31');

    expect(await screen.findByText(EMPTY_RANGE_TEXT)).toBeInTheDocument();
    expect(pointButtons()).toHaveLength(0);
    expect(screen.getByText('Valor del jugador')).toBeInTheDocument();
  });

  it('"Todo el historial" vuelve a mostrar todas las semanas', async () => {
    const { user } = openPlayer();
    await waitFor(() => expect(pointButtons()).toHaveLength(WEEKS.length));
    await user.type(screen.getByLabelText('Desde'), '2025-01-01');
    await user.type(screen.getByLabelText('Hasta'), '2025-01-31');
    await screen.findByText(EMPTY_RANGE_TEXT);

    await user.click(screen.getByRole('button', { name: 'Todo el historial' }));

    await waitFor(() => expect(pointButtons()).toHaveLength(WEEKS.length));
  });

  it('un error de la evolución ofrece reintentar', async () => {
    server.use(http.get('*/api/players/:id/quotes', () => HttpResponse.error(), { once: true }));
    const { user } = openPlayer();

    await user.click(await screen.findByRole('button', { name: 'reintentar la evolución' }));

    await waitFor(() => expect(pointButtons()).toHaveLength(WEEKS.length));
  });
});
