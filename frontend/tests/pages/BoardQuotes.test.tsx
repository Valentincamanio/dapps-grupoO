// Historia 3 de la 007: la cotización de cada post-it de la pizarra (FR-062).
import { screen, waitFor, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { SHEET_SIZE } from '../../src/utils/filters';
import { formatCredits, formatVariationAmount, variationText } from '../../src/utils/quotes';
import { PLAYERS } from '../mocks/players';
import { currentQuoteOf, PLAYER_WITHOUT_QUOTE_ID } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const BATCH_PATH = '/api/players/quotes/current';

// Pedidos de cotizaciones hechos durante el test: el lote y los de a un jugador
function recordQuoteRequests() {
  const batch: URL[] = [];
  const single: URL[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname === BATCH_PATH) batch.push(url);
    else if (/^\/api\/players\/\d+\/quotes/.test(url.pathname)) single.push(url);
  });
  return { batch, single };
}

function postItOf(playerId: number): HTMLElement {
  const grid = screen.getByRole('list');
  return within(grid)
    .getAllByRole('link')
    .find((link) => link.getAttribute('href') === `/jugadores/${playerId}`)!;
}

afterEach(() => server.events.removeAllListeners());

describe('BoardPage - cotizaciones', () => {
  it('cada post-it muestra la cotización actual con su variación en signo y texto', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');

    const player = PLAYERS[0];
    const quote = currentQuoteOf(player.id)!;
    const postIt = postItOf(player.id);

    await waitFor(() => expect(postIt).toHaveTextContent(formatCredits(quote.value)));
    expect(postIt).toHaveTextContent(formatVariationAmount(quote.variation!));
    expect(postIt).toHaveTextContent(variationText(quote.variation!));
  });

  it('pide una sola consulta de lote por hoja, con los ids de la hoja, y ninguna por jugador', async () => {
    const requests = recordQuoteRequests();
    const { user } = renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');
    await waitFor(() => expect(requests.batch).toHaveLength(1));

    const firstSheetIds = PLAYERS.slice(0, SHEET_SIZE).map((player) => player.id).join(',');
    expect(requests.batch[0].searchParams.get('ids')).toBe(firstSheetIds);

    await user.click(screen.getByRole('button', { name: 'hoja siguiente' }));
    await screen.findByText('hoja 2 de 5');

    await waitFor(() => expect(requests.batch).toHaveLength(2));
    const secondSheetIds = PLAYERS.slice(SHEET_SIZE, SHEET_SIZE * 2).map((player) => player.id);
    expect(requests.batch[1].searchParams.get('ids')).toBe(secondSheetIds.join(','));
    expect(requests.single).toHaveLength(0);
  });

  it('un jugador sin cotización lo indica sin inventar un valor y la pizarra sigue funcionando', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');

    const postIt = postItOf(PLAYER_WITHOUT_QUOTE_ID);

    await waitFor(() => expect(postIt).toHaveTextContent('sin cotización'));
    expect(postIt).not.toHaveTextContent(/cr\./);
    expect(within(screen.getByRole('list')).getAllByRole('link')).toHaveLength(SHEET_SIZE);
  });

  it('mientras llega el lote, cada post-it muestra que la cotización está cargando', async () => {
    server.use(http.get(`*${BATCH_PATH}`, async () => delay('infinite')));
    renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');

    const links = within(screen.getByRole('list')).getAllByRole('link');

    links.forEach((link) => expect(link).toHaveTextContent('cargando cotización...'));
  });

  it('un error del lote no rompe la pizarra y "reintentar cotizaciones" las carga', async () => {
    server.use(http.get(`*${BATCH_PATH}`, () => HttpResponse.error(), { once: true }));
    const { user } = renderWithRouter({ initialEntries: ['/pizarra'] });

    const retry = await screen.findByRole('button', { name: 'reintentar cotizaciones' });

    expect(screen.getByRole('alert')).toHaveTextContent('no se pudieron cargar las cotizaciones');
    expect(screen.getByText('hoja 1 de 5')).toBeInTheDocument();
    const links = within(screen.getByRole('list')).getAllByRole('link');
    expect(links).toHaveLength(SHEET_SIZE);
    links.forEach((link) => expect(link).toHaveTextContent('cotización no disponible'));

    await user.click(retry);

    const quote = currentQuoteOf(PLAYERS[0].id)!;
    await waitFor(() => expect(postItOf(PLAYERS[0].id)).toHaveTextContent(formatCredits(quote.value)));
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('con cotizaciones sigue habiendo una única lista y cada post-it es un único link', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');
    await waitFor(() => expect(postItOf(PLAYERS[0].id)).toHaveTextContent(/cr\./));

    expect(screen.getAllByRole('list')).toHaveLength(1);
    const grid = screen.getByRole('list');
    expect(within(grid).queryAllByRole('button')).toHaveLength(0);
    within(grid)
      .getAllByRole('listitem')
      .forEach((item) => expect(within(item).getAllByRole('link')).toHaveLength(1));
  });
});
