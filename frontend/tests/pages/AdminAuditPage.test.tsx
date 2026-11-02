// La auditoría del mercado en la administración, como ADMIN: los registros del más nuevo al más
// viejo, con el jugador, el autor y cómo quedó cada parte, y los filtros.
import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { AUDIT_ENTRIES, BUYER_ID, SELLER_ID } from '../mocks/auditHandlers';
import { PLAYERS } from '../mocks/players';
import { adminProfileHandler } from '../mocks/quoteHandlers';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

async function openPage() {
  const utils = renderWithRouter({ initialEntries: ['/admin/auditoria'], session: 'valid' });
  await screen.findByRole('list', { name: 'registros de auditoría' });
  return utils;
}

function entries(): HTMLElement[] {
  return within(screen.getByRole('list', { name: 'registros de auditoría' })).getAllByRole('article');
}

describe('AdminAuditPage', () => {
  beforeEach(() => {
    server.use(adminProfileHandler);
  });

  it('muestra los registros del más nuevo al más viejo, con la pestaña marcada', async () => {
    await openPage();

    expect(entries().map((entry) => entry.getAttribute('aria-label'))).toEqual([
      'cancelación 3',
      'operación 2',
      'orden creada 1',
    ]);
    expect(screen.getByText('3 registros, del más nuevo al más viejo')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Auditoría', current: 'page' })).toBeInTheDocument();
  });

  it('una operación muestra el jugador, el autor y cómo quedaron el comprador y el vendedor', async () => {
    await openPage();

    const trade = entries()[1];
    expect(await within(trade).findByRole('link', { name: PLAYERS[0].name })).toHaveAttribute(
      'href',
      `/jugadores/${PLAYERS[0].id}`,
    );
    expect(within(trade).getByText(`por usuario #${BUYER_ID}`)).toBeInTheDocument();
    expect(within(trade).getByText(/^compra de 10 tokens a 1,0000 cr\. · total 10,00 cr\.$/)).toBeInTheDocument();
    const buyer = within(trade).getByRole('rowheader', { name: `comprador · usuario #${BUYER_ID}` }).closest('tr')!;
    expect(within(buyer).getByText('1.000,00 → 990,00')).toBeInTheDocument();
    expect(within(buyer).getByText('0 → 10')).toBeInTheDocument();
    expect(within(trade).getByRole('rowheader', { name: `vendedor · usuario #${SELLER_ID}` })).toBeInTheDocument();
  });

  it('la orden inicial dice que la hizo el superusuario y la cancelación, el motivo', async () => {
    await openPage();

    expect(within(entries()[2]).getByText('por superusuario')).toBeInTheDocument();
    expect(within(entries()[0]).getByText('la canceló el usuario')).toBeInTheDocument();
  });

  it('filtra por tipo de hecho', async () => {
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'operaciones' }));

    await waitFor(() => expect(entries()).toHaveLength(1));
    expect(entries()[0]).toHaveAccessibleName('operación 2');
  });

  it('filtra por usuario, como autor o como cualquiera de las partes', async () => {
    const { user } = await openPage();

    await user.type(screen.getByLabelText('usuario #'), String(SELLER_ID));
    await user.click(screen.getByRole('button', { name: 'Filtrar' }));

    await waitFor(() => expect(entries()).toHaveLength(2));
    expect(entries().map((entry) => entry.getAttribute('aria-label'))).toEqual(['operación 2', 'orden creada 1']);
  });

  it('filtra por jugador elegido en el buscador y se puede quitar', async () => {
    const { user } = await openPage();

    await user.type(screen.getByRole('combobox', { name: 'buscar jugador' }), PLAYERS[1].name);
    await user.click(await screen.findByRole('option', { name: new RegExp(PLAYERS[1].name) }));
    await waitFor(() => expect(entries()).toHaveLength(1));
    await user.click(screen.getByRole('button', { name: 'quitar' }));

    await waitFor(() => expect(entries()).toHaveLength(3));
  });

  it('filtra por rango de fechas y sin resultados ofrece limpiar los filtros', async () => {
    const { user } = await openPage();

    await user.type(screen.getByLabelText('desde'), '2026-11-15');

    expect(await screen.findByText('no hay registros con esos filtros')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'limpiar filtros' }));
    await waitFor(() => expect(entries()).toHaveLength(AUDIT_ENTRIES.length));
  });

  it('si la auditoría no responde, lo dice y deja reintentar', async () => {
    server.use(http.get('*/api/audit', () => HttpResponse.error()));

    renderWithRouter({ initialEntries: ['/admin/auditoria'], session: 'valid' });

    expect(await screen.findByText('no se pudo conectar con la pizarra')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'reintentar' })).toBeInTheDocument();
  });
});
