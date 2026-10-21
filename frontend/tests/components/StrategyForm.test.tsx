// Historia 8 de la 007: el formulario de estrategias (FR-066): no deja enviar pesos que no suman
// 1,00 y un rechazo del servidor no pierde lo escrito.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { StrategyForm } from '../../src/components/StrategyForm';
import { seedStrategies } from '../mocks/quotes';

const [ACTIVE, INACTIVE] = seedStrategies();

describe('StrategyForm', () => {
  it('al crear no deja enviar hasta que hay nombre y el juego suma 1,00', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<StrategyForm onSubmit={onSubmit} />);
    const submit = screen.getByRole('button', { name: 'Crear estrategia' });

    expect(submit).toBeDisabled();
    await user.type(screen.getByLabelText('nombre'), 'Ofensiva');
    await user.type(screen.getByLabelText('goles'), '0,6');
    expect(submit).toBeDisabled();
    await user.type(screen.getByLabelText('asistencias'), '0,35');
    expect(submit).toBeDisabled();
    await user.click(submit);
    expect(onSubmit).not.toHaveBeenCalled();

    await user.clear(screen.getByLabelText('asistencias'));
    await user.type(screen.getByLabelText('asistencias'), '0,4');
    expect(submit).toBeEnabled();
    await user.click(submit);

    expect(onSubmit).toHaveBeenCalledWith({
      name: 'Ofensiva',
      input: { scaleFactor: 10, minimumValue: 10, defaultWeights: { GOALS: 0.6, ASSISTS: 0.4 } },
      requote: undefined,
    });
  });

  it('un juego por posición también tiene que sumar 1,00 y se puede quitar', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<StrategyForm onSubmit={onSubmit} />);
    await user.type(screen.getByLabelText('nombre'), 'Por puesto');
    await user.type(screen.getByLabelText('goles'), '1');

    await user.click(screen.getByRole('button', { name: 'agregar juego de arqueros' }));
    const goalkeepers = screen.getByRole('group', { name: 'juego de arqueros' });
    // Arranca con una copia del juego por defecto
    expect(within(goalkeepers).getByLabelText('goles')).toHaveValue('1');
    await user.clear(within(goalkeepers).getByLabelText('goles'));
    await user.type(within(goalkeepers).getByLabelText('valla invicta'), '0,5');
    expect(screen.getByRole('button', { name: 'Crear estrategia' })).toBeDisabled();

    await user.type(within(goalkeepers).getByLabelText('despejes'), '0,5');
    await user.click(screen.getByRole('button', { name: 'Crear estrategia' }));
    expect(onSubmit).toHaveBeenLastCalledWith(
      expect.objectContaining({
        input: expect.objectContaining({
          defaultWeights: { GOALS: 1 },
          positionWeights: { GOALKEEPER: { CLEARANCES: 0.5, CLEAN_SHEETS: 0.5 } },
        }),
      }),
    );

    await user.click(screen.getByRole('button', { name: 'quitar juego de arqueros' }));
    expect(screen.queryByRole('group', { name: 'juego de arqueros' })).toBeNull();
  });

  it('valida el factor y el mínimo', async () => {
    const user = userEvent.setup();
    render(<StrategyForm onSubmit={vi.fn()} />);
    await user.type(screen.getByLabelText('nombre'), 'Ofensiva');
    await user.type(screen.getByLabelText('goles'), '1');

    await user.clear(screen.getByLabelText('factor de escala'));
    await user.type(screen.getByLabelText('factor de escala'), '0');
    await user.clear(screen.getByLabelText('cotización mínima (cr.)'));

    expect(screen.getByLabelText('factor de escala')).toHaveAccessibleDescription('tiene que ser mayor que 0');
    expect(screen.getByLabelText('cotización mínima (cr.)')).toHaveAccessibleDescription('es obligatorio');
    expect(screen.getByRole('button', { name: 'Crear estrategia' })).toBeDisabled();
  });

  it('muestra el error del servidor en español sin perder lo escrito', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    const { rerender } = render(<StrategyForm onSubmit={onSubmit} />);
    await user.type(screen.getByLabelText('nombre'), 'Rendimiento de partido');
    await user.type(screen.getByLabelText('goles'), '1');
    await user.click(screen.getByRole('button', { name: 'Crear estrategia' }));

    const message = "Ya existe una estrategia con el nombre 'Rendimiento de partido'.";
    rerender(<StrategyForm onSubmit={onSubmit} error={message} />);

    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(screen.getByLabelText('nombre')).toHaveValue('Rendimiento de partido');
    expect(screen.getByLabelText('goles')).toHaveValue('1');
  });

  it('al editar parte de la versión, no pide nombre y conserva los juegos por posición', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    const version = INACTIVE.versions[0];
    render(<StrategyForm version={version} onSubmit={onSubmit} />);

    expect(screen.queryByLabelText('nombre')).toBeNull();
    expect(screen.queryByRole('radio')).toBeNull();
    const defaults = screen.getByRole('group', { name: 'juego por defecto' });
    expect(within(defaults).getByLabelText('goles')).toHaveValue('0,25');
    expect(screen.getByRole('group', { name: 'juego de delanteros' })).toBeInTheDocument();

    await user.clear(screen.getByLabelText('factor de escala'));
    await user.type(screen.getByLabelText('factor de escala'), '12,5');
    await user.click(screen.getByRole('button', { name: 'Guardar versión nueva' }));

    expect(onSubmit).toHaveBeenCalledWith({
      name: undefined,
      input: {
        scaleFactor: 12.5,
        minimumValue: 10,
        defaultWeights: version.defaultWeights,
        positionWeights: version.positionWeights,
      },
      requote: undefined,
    });
  });

  it('al editar la activa pide cuándo recotizar, con la semana siguiente preseleccionada', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<StrategyForm version={ACTIVE.versions[0]} isActive onSubmit={onSubmit} />);

    expect(screen.getByRole('radio', { name: 'Recotizar la semana siguiente' })).toBeChecked();
    await user.click(screen.getByRole('button', { name: 'Guardar versión nueva' }));
    expect(onSubmit).toHaveBeenLastCalledWith(expect.objectContaining({ requote: 'PROXIMA_SEMANA' }));

    await user.click(screen.getByRole('radio', { name: 'Recotizar ahora' }));
    await user.click(screen.getByRole('button', { name: 'Guardar versión nueva' }));
    expect(onSubmit).toHaveBeenLastCalledWith(expect.objectContaining({ requote: 'AHORA' }));
  });
});
