// Historia 8 de la 007: la pantalla de estrategias (FR-065, FR-066, FR-068), como ADMIN.
import { screen, waitFor, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { adminProfileHandler, mockStrategies, resetAdminMocks, setAdminConflict } from '../mocks/quoteHandlers';
import { RECALCULATION_IN_PROGRESS, STRATEGY_A, STRATEGY_B } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

function openPage() {
  return renderWithRouter({ initialEntries: ['/admin/estrategias'], session: 'valid' });
}

async function strategyCard(name: string): Promise<HTMLElement> {
  return (await screen.findByRole('heading', { name })).closest('article') as HTMLElement;
}

function activeName(): string | undefined {
  return mockStrategies().find((strategy) => strategy.active)?.name;
}

describe('AdminStrategiesPage', () => {
  beforeEach(() => {
    resetAdminMocks();
    server.use(adminProfileHandler);
  });

  it('lista cada estrategia con sus versiones y cuál es la activa', async () => {
    openPage();

    const active = await strategyCard(STRATEGY_A.name);
    const inactive = await strategyCard(STRATEGY_B.name);

    expect(within(active).getByText('activa')).toBeInTheDocument();
    expect(within(active).getByText('versión 1')).toBeInTheDocument();
    expect(within(active).getByText('(activa)')).toBeInTheDocument();
    expect(within(active).queryByRole('button', { name: /^Activar/ })).toBeNull();
    expect(within(inactive).queryByText('activa')).toBeNull();
    expect(within(inactive).getByRole('button', { name: `Activar ${STRATEGY_B.name}` })).toBeInTheDocument();
    expect(within(inactive).getByRole('button', { name: `Editar ${STRATEGY_B.name}` })).toBeInTheDocument();
  });

  it('muestra la carga y, si falla, el error con reintentar', async () => {
    let fail = true;
    server.use(
      http.get('*/api/strategies', async () => {
        await delay(50);
        if (fail) return HttpResponse.json({ status: 500, message: 'Error interno.' }, { status: 500 });
        return HttpResponse.json([]);
      }),
    );
    const { user } = openPage();

    // Pasada la guarda de sesión, la lista todavía está cargando
    await screen.findByRole('heading', { name: 'Estrategias' });
    expect(screen.getByText('el DT está pensando...')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent('no se pudieron cargar las estrategias');

    fail = false;
    await user.click(screen.getByRole('button', { name: 'reintentar' }));
    expect(await screen.findByText('todavía no hay estrategias')).toBeInTheDocument();
  });

  it('crear una estrategia la agrega a la lista', async () => {
    const { user } = openPage();
    await strategyCard(STRATEGY_A.name);

    await user.click(screen.getByRole('button', { name: 'Nueva estrategia' }));
    expect(screen.getByRole('heading', { name: 'Nueva estrategia' })).toHaveFocus();
    await user.type(screen.getByLabelText('nombre'), 'Ofensiva');
    await user.type(screen.getByLabelText('goles'), '0,6');
    await user.type(screen.getByLabelText('asistencias'), '0,4');
    await user.click(screen.getByRole('button', { name: 'Crear estrategia' }));

    expect(await screen.findByText('se creó la estrategia Ofensiva, inactiva')).toBeInTheDocument();
    const created = await strategyCard('Ofensiva');
    expect(within(created).getByText('versión 1')).toBeInTheDocument();
    expect(mockStrategies().map((strategy) => strategy.name)).toContain('Ofensiva');
    expect(activeName()).toBe(STRATEGY_A.name);
  });

  it('un nombre repetido muestra el motivo del 409 y conserva lo escrito', async () => {
    const { user } = openPage();
    await strategyCard(STRATEGY_A.name);

    await user.click(screen.getByRole('button', { name: 'Nueva estrategia' }));
    await user.type(screen.getByLabelText('nombre'), STRATEGY_A.name);
    await user.type(screen.getByLabelText('goles'), '1');
    await user.click(screen.getByRole('button', { name: 'Crear estrategia' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      `Ya existe una estrategia con el nombre '${STRATEGY_A.name}'.`,
    );
    expect(screen.getByLabelText('nombre')).toHaveValue(STRATEGY_A.name);
    expect(screen.getByLabelText('goles')).toHaveValue('1');
    expect(mockStrategies()).toHaveLength(2);
  });

  it('editar crea la versión siguiente y la lista la muestra', async () => {
    const { user } = openPage();

    await user.click(
      within(await strategyCard(STRATEGY_B.name)).getByRole('button', { name: `Editar ${STRATEGY_B.name}` }),
    );
    expect(screen.getByText('se guarda como la versión 2, a partir de la versión 1')).toBeInTheDocument();
    // B no es la activa: no se pregunta cuándo recotizar
    expect(screen.queryByRole('radio')).toBeNull();
    await user.clear(screen.getByLabelText('factor de escala'));
    await user.type(screen.getByLabelText('factor de escala'), '12');
    await user.click(screen.getByRole('button', { name: 'Guardar versión nueva' }));

    expect(await screen.findByText(`se guardó la versión 2 de ${STRATEGY_B.name}`)).toBeInTheDocument();
    const card = await strategyCard(STRATEGY_B.name);
    expect(within(card).getByText('versión 1')).toBeInTheDocument();
    expect(within(card).getByText('versión 2')).toBeInTheDocument();
    const saved = mockStrategies().find((strategy) => strategy.id === STRATEGY_B.id)!;
    expect(saved.versions[1].scaleFactor).toBe(12);
  });

  it('editar la activa pide cuándo recotizar y con "ahora" muestra el informe', async () => {
    const { user } = openPage();

    await user.click(
      within(await strategyCard(STRATEGY_A.name)).getByRole('button', { name: `Editar ${STRATEGY_A.name}` }),
    );
    expect(screen.getByRole('radio', { name: 'Recotizar la semana siguiente' })).toBeChecked();
    await user.click(screen.getByRole('radio', { name: 'Recotizar ahora' }));
    await user.click(screen.getByRole('button', { name: 'Guardar versión nueva' }));

    expect(await screen.findByText(`se guardó la versión 2 de ${STRATEGY_A.name}`)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'informe de la recotización' })).toBeInTheDocument();
  });

  it('activar exige elegir cuándo recotizar y no cambia nada hasta confirmar', async () => {
    const { user } = openPage();

    await user.click(
      within(await strategyCard(STRATEGY_B.name)).getByRole('button', { name: `Activar ${STRATEGY_B.name}` }),
    );

    expect(screen.getByRole('heading', { name: `Activar ${STRATEGY_B.name}` })).toHaveFocus();
    expect(screen.getByRole('radio', { name: 'Recotizar la semana siguiente' })).toBeChecked();
    await user.click(screen.getByRole('radio', { name: 'Recotizar ahora' }));
    await user.click(screen.getByRole('radio', { name: 'Recotizar la semana siguiente' }));
    expect(activeName()).toBe(STRATEGY_A.name);

    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(
      await screen.findByText(
        `${STRATEGY_B.name} es ahora la estrategia activa y las cotizaciones cambian desde el próximo cierre semanal`,
      ),
    ).toBeInTheDocument();
    expect(activeName()).toBe(STRATEGY_B.name);
    expect(screen.queryByRole('heading', { name: 'informe de la recotización' })).toBeNull();
    await waitFor(async () =>
      expect(within(await strategyCard(STRATEGY_B.name)).getByText('activa')).toBeInTheDocument(),
    );
  });

  it('activar con "recotizar ahora" muestra el informe de la recotización', async () => {
    const { user } = openPage();

    await user.click(
      within(await strategyCard(STRATEGY_B.name)).getByRole('button', { name: `Activar ${STRATEGY_B.name}` }),
    );
    await user.click(screen.getByRole('radio', { name: 'Recotizar ahora' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(
      await screen.findByText(`${STRATEGY_B.name} es ahora la estrategia activa y se recotizó la última semana cerrada`),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'informe de la recotización' })).toBeInTheDocument();
  });

  it('cancelar la activación no cambia nada', async () => {
    const { user } = openPage();

    await user.click(
      within(await strategyCard(STRATEGY_B.name)).getByRole('button', { name: `Activar ${STRATEGY_B.name}` }),
    );
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(await screen.findByRole('heading', { name: 'Estrategias' })).toHaveFocus();
    expect(activeName()).toBe(STRATEGY_A.name);
  });

  it('un 409 de "recotizar ahora" muestra el motivo y conserva la elección', async () => {
    setAdminConflict(RECALCULATION_IN_PROGRESS);
    const { user } = openPage();

    await user.click(
      within(await strategyCard(STRATEGY_B.name)).getByRole('button', { name: `Activar ${STRATEGY_B.name}` }),
    );
    await user.click(screen.getByRole('radio', { name: 'Recotizar ahora' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(RECALCULATION_IN_PROGRESS);
    expect(screen.getByRole('heading', { name: `Activar ${STRATEGY_B.name}` })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Recotizar ahora' })).toBeChecked();
    expect(activeName()).toBe(STRATEGY_A.name);
  });

  it('un 409 al editar la activa con "ahora" muestra el motivo y conserva lo escrito', async () => {
    setAdminConflict(RECALCULATION_IN_PROGRESS);
    const { user } = openPage();

    await user.click(
      within(await strategyCard(STRATEGY_A.name)).getByRole('button', { name: `Editar ${STRATEGY_A.name}` }),
    );
    await user.clear(screen.getByLabelText('factor de escala'));
    await user.type(screen.getByLabelText('factor de escala'), '15');
    await user.click(screen.getByRole('radio', { name: 'Recotizar ahora' }));
    await user.click(screen.getByRole('button', { name: 'Guardar versión nueva' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(RECALCULATION_IN_PROGRESS);
    expect(screen.getByLabelText('factor de escala')).toHaveValue('15');
    expect(screen.getByRole('radio', { name: 'Recotizar ahora' })).toBeChecked();
    expect(mockStrategies()[0].versions).toHaveLength(1);
  });
});
