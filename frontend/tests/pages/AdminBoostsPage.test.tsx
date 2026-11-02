// Potenciados en la administración: elegir 4 jugadores por semana y cuánto se potencia cada uno, la
// semana en curso con confirmación y las cerradas como registro. La semana en curso del backend
// simulado es la del lunes 26/10/2026.
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { BOOST_CURRENT_MONDAY, mockBoosts, setMockBoosts } from '../mocks/boostHandlers';
import { PLAYERS } from '../mocks/players';
import { adminProfileHandler } from '../mocks/quoteHandlers';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const NEXT_MONDAY = '2026-11-02';
const PREVIOUS_MONDAY = '2026-10-19';
const FOUR = PLAYERS.slice(0, 4);

async function openPage() {
  const utils = renderWithRouter({ initialEntries: ['/admin/potenciados'], session: 'valid' });
  await screen.findByText('esta semana');
  return utils;
}

async function addFour(user: Awaited<ReturnType<typeof openPage>>['user']) {
  for (const player of FOUR) {
    await user.click(await screen.findByRole('button', { name: `agregar a ${player.name}` }));
  }
}

function slots(): HTMLElement {
  return screen.getByRole('list', { name: 'potenciados de la semana' });
}

describe('AdminBoostsPage', () => {
  beforeEach(() => {
    server.use(adminProfileHandler);
  });

  it('arranca en la semana en curso, sin potenciados, con cuatro lugares libres', async () => {
    await openPage();

    expect(await screen.findByText('sin elegir')).toBeInTheDocument();
    expect(within(slots()).getAllByText('lugar libre', { exact: false })).toHaveLength(4);
    expect(screen.getByRole('button', { name: 'Guardar potenciados' })).toBeDisabled();
    expect(screen.getByRole('link', { name: 'Potenciados', current: 'page' })).toBeInTheDocument();
  });

  it('elige cuatro para la próxima semana, ajusta el porcentaje y los guarda sin pedir confirmación', async () => {
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'semana siguiente' }));
    await screen.findByText('próxima semana');
    await addFour(user);
    fireEvent.change(within(slots()).getAllByRole('slider')[0], { target: { value: '300' } });
    await user.click(screen.getByRole('button', { name: 'Guardar potenciados' }));

    expect(await screen.findByText(/^listo: los 4 potenciados rigen en el cierre/)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(mockBoosts(NEXT_MONDAY)).toEqual([
      { playerId: FOUR[0].id, percent: 300 },
      { playerId: FOUR[1].id, percent: 200 },
      { playerId: FOUR[2].id, percent: 200 },
      { playerId: FOUR[3].id, percent: 200 },
    ]);
    expect(await screen.findByText('programada')).toBeInTheDocument();
  });

  it('con los cuatro elegidos no deja agregar a nadie más y con tres no deja guardar', async () => {
    const { user } = await openPage();

    await addFour(user);
    const fifthDisabledWhenFull = screen
      .getByRole('button', { name: `agregar a ${PLAYERS[4].name}` })
      .hasAttribute('disabled');
    const saveEnabledWhenFull = !screen.getByRole('button', { name: 'Guardar potenciados' }).hasAttribute('disabled');
    await user.click(screen.getByRole('button', { name: `quitar a ${FOUR[3].name}` }));

    expect(fifthDisabledWhenFull).toBe(true);
    expect(saveEnabledWhenFull).toBe(true);
    expect(screen.getByRole('button', { name: 'Guardar potenciados' })).toBeDisabled();
    expect(screen.getByText('3 de 4 elegidos')).toBeInTheDocument();
  });

  it('cambiar la semana en curso pide confirmación y, al volver, no guarda nada', async () => {
    const { user } = await openPage();

    await addFour(user);
    await user.click(screen.getByRole('button', { name: 'Guardar potenciados' }));
    const dialog = screen.getByRole('dialog', { name: '¿Cambiar los potenciados de esta semana?' });
    await user.click(within(dialog).getByRole('button', { name: 'Volver' }));

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(mockBoosts(BOOST_CURRENT_MONDAY)).toEqual([]);
  });

  it('confirmando, los potenciados de la semana en curso cambian', async () => {
    setMockBoosts(BOOST_CURRENT_MONDAY, FOUR.map((player) => ({ playerId: player.id, percent: 250 })));
    const { user } = await openPage();

    await user.click(await screen.findByRole('button', { name: `quitar a ${FOUR[0].name}` }));
    await user.click(screen.getByRole('button', { name: `agregar a ${PLAYERS[4].name}` }));
    await user.click(screen.getByRole('button', { name: 'Guardar potenciados' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText(new RegExp(`Quedan: ${FOUR[1].name} \\(250 %\\)`))).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Sí, cambiarlos' }));

    expect(await screen.findByText('listo: se actualizaron los potenciados de esta semana')).toBeInTheDocument();
    expect(mockBoosts(BOOST_CURRENT_MONDAY).map((boost) => boost.playerId)).toEqual([
      FOUR[1].id,
      FOUR[2].id,
      FOUR[3].id,
      PLAYERS[4].id,
    ]);
  });

  it('quitar a los cuatro y guardar deja la semana sin potenciados', async () => {
    setMockBoosts(NEXT_MONDAY, FOUR.map((player) => ({ playerId: player.id, percent: 200 })));
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'semana siguiente' }));
    for (const player of FOUR) {
      await user.click(await screen.findByRole('button', { name: `quitar a ${player.name}` }));
    }
    await user.click(screen.getByRole('button', { name: 'Guardar potenciados' }));

    expect(await screen.findByText('listo: la semana quedó sin potenciados')).toBeInTheDocument();
    expect(mockBoosts(NEXT_MONDAY)).toEqual([]);
  });

  it('una semana cerrada se muestra como registro, sin elegir ni guardar', async () => {
    setMockBoosts(PREVIOUS_MONDAY, FOUR.map((player) => ({ playerId: player.id, percent: 300 })));
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'semana anterior' }));

    expect(await screen.findByText('Semana cerrada: sus potenciados quedan como registro.')).toBeInTheDocument();
    expect(screen.getByText('cerrada')).toBeInTheDocument();
    expect(within(slots()).getAllByText('300 %')).toHaveLength(4);
    expect(screen.queryByRole('button', { name: 'Guardar potenciados' })).toBeNull();
    expect(screen.queryByRole('region', { name: 'Elegir jugadores' })).toBeNull();
  });

  it('lo elegido sin guardar se conserva al ir y volver de semana, y descartar lo borra', async () => {
    const { user } = await openPage();

    await user.click(await screen.findByRole('button', { name: `agregar a ${FOUR[0].name}` }));
    await user.click(screen.getByRole('button', { name: 'semana siguiente' }));
    await screen.findByText('próxima semana');
    await user.click(screen.getByRole('button', { name: 'semana anterior' }));
    await screen.findByText('esta semana');
    const kept = await within(slots()).findByText(FOUR[0].name);
    await user.click(screen.getByRole('button', { name: 'Descartar' }));

    expect(kept).toHaveTextContent(FOUR[0].name);
    await waitFor(() => expect(within(slots()).queryByText(FOUR[0].name)).toBeNull());
  });
});
