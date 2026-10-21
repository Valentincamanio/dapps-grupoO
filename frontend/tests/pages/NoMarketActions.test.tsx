// Feature 007, FR-071 y SC-020: las pantallas con cotizaciones y las de administración no ofrecen
// operar en el mercado ni muestran datos de inversión del usuario. El precio por token del jugador
// sí puede aparecer, como texto.
import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { formatCredits } from '../../src/utils/quotes';
import { PLAYERS } from '../mocks/players';
import { adminProfileHandler, resetAdminMocks } from '../mocks/quoteHandlers';
import { currentQuoteOf, STRATEGY_A, STRATEGY_B } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const MARKET_ACTION = /compra|comprar|vend|sumar|plantel|orden/i;
const INVESTMENT_DATA = /presupuesto|portfolio|cartera|\btokens\b|mis acciones/i;

function expectNoMarketActions() {
  const actionable = [...screen.queryAllByRole('button'), ...screen.queryAllByRole('link')];
  const offending = actionable
    .map((element) => element.getAttribute('aria-label') ?? element.textContent ?? '')
    .filter((name) => MARKET_ACTION.test(name));
  expect(offending).toEqual([]);

  expect(document.body.textContent ?? '').not.toMatch(INVESTMENT_DATA);
}

function renderAsAdmin(path: string) {
  return renderWithRouter({ initialEntries: [path], session: 'valid' });
}

describe('pantallas de la 007 sin acciones de mercado', () => {
  beforeEach(() => {
    resetAdminMocks();
    server.use(adminProfileHandler);
  });

  it('/pizarra, con las cotizaciones cargadas', async () => {
    renderAsAdmin('/pizarra');

    await screen.findByText(/^hoja 1 de /);
    const quote = currentQuoteOf(PLAYERS[0].id)!;
    await waitFor(() => expect(screen.getByRole('list')).toHaveTextContent(formatCredits(quote.value)));
    await screen.findByRole('link', { name: 'Administración' });

    expectNoMarketActions();
  });

  it('/jugadores/<id>, que muestra el precio por token como texto', async () => {
    renderAsAdmin(`/jugadores/${PLAYERS[0].id}`);

    await screen.findByRole('heading', { name: PLAYERS[0].name });
    expect(await screen.findByText('Precio por token')).toBeInTheDocument();

    expectNoMarketActions();
  });

  it('/ranking', async () => {
    renderAsAdmin('/ranking');

    await screen.findByText(/^hoja 1 de /);

    expectNoMarketActions();
  });

  it('/admin/estrategias, con el formulario y la elección de recotización abiertos', async () => {
    const { user } = renderAsAdmin('/admin/estrategias');

    await screen.findByRole('heading', { name: STRATEGY_A.name });
    expectNoMarketActions();

    await user.click(screen.getByRole('button', { name: 'Nueva estrategia' }));
    await screen.findByRole('heading', { name: 'Nueva estrategia' });
    expectNoMarketActions();
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    await user.click(await screen.findByRole('button', { name: `Activar ${STRATEGY_B.name}` }));
    await screen.findByRole('heading', { name: `Activar ${STRATEGY_B.name}` });
    expectNoMarketActions();
  });

  it('/admin/recalculo, con el informe a la vista', async () => {
    const { user } = renderAsAdmin('/admin/recalculo');

    await user.click(await screen.findByRole('button', { name: 'Recalcular cotizaciones' }));
    await screen.findByRole('region', { name: 'informe del recálculo' });

    expectNoMarketActions();
  });

  it('/admin/simulacion, con la tabla simulada', async () => {
    const { user } = renderAsAdmin('/admin/simulacion');

    await screen.findByRole('combobox', { name: 'estrategia' });
    await user.click(screen.getByRole('button', { name: 'Simular' }));
    await screen.findByText(/semanas simuladas$/);
    expect(screen.getByRole('table')).toBeInTheDocument();

    expectNoMarketActions();
  });
});
