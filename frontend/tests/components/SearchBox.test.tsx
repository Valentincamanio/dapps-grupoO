import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SearchBox } from '../../src/components/SearchBox';
import { PLAYERS, SHARED_NAME_TEXT } from '../mocks/players';

type Status = 'idle' | 'loading' | 'success' | 'error';

function setup(status: Status = 'success') {
  const onSelect = vi.fn();
  const onFocus = vi.fn();
  const onRetry = vi.fn();
  const user = userEvent.setup();
  render(
    <SearchBox
      players={PLAYERS}
      status={status}
      onFocus={onFocus}
      onRetry={onRetry}
      onSelect={onSelect}
    />,
  );
  const input = screen.getByRole('combobox');
  return { user, input, onSelect, onFocus, onRetry };
}

// El debounce es de 200 ms: se espera con timers reales a que aparezca el panel
async function typeAndWait(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.type(screen.getByRole('combobox'), text);
  await settle();
}

// Espera un tiempo mayor al debounce, dentro de act para que React aplique la consulta
async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 350));
  });
}

describe('SearchBox', () => {
  it('enfocar el campo llama onFocus', async () => {
    const { user, input, onFocus } = setup();

    await user.click(input);

    expect(onFocus).toHaveBeenCalled();
  });

  it('una letra no muestra sugerencias', async () => {
    const { user } = setup();

    await typeAndWait(user, 'm');

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('no muestra sugerencias antes de que pase el debounce', async () => {
    const { user } = setup();

    await user.type(screen.getByRole('combobox'), 'mbappe');

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('"mbappe" muestra "Kylian Mbappé" con posición y equipo', async () => {
    const { user } = setup();

    await typeAndWait(user, 'mbappe');

    const option = screen.getByRole('option');
    expect(option).toHaveTextContent('Kylian Mbappé');
    expect(option).toHaveTextContent('Delantero');
    expect(option).toHaveTextContent('Real Madrid');
  });

  it('más de 8 coincidencias muestran 8', async () => {
    const { user } = setup();

    await typeAndWait(user, SHARED_NAME_TEXT);

    expect(screen.getAllByRole('option')).toHaveLength(8);
  });

  it('"zzz" muestra "no hay nadie con ese nombre en la pizarra"', async () => {
    const { user } = setup();

    await typeAndWait(user, 'zzz');

    expect(screen.getByText('no hay nadie con ese nombre en la pizarra')).toBeInTheDocument();
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('las flechas cambian aria-activedescendant', async () => {
    const { user, input } = setup();
    await typeAndWait(user, SHARED_NAME_TEXT);
    const options = screen.getAllByRole('option');
    expect(input).toHaveAttribute('aria-activedescendant', options[0].id);

    await user.keyboard('{ArrowDown}');
    expect(input).toHaveAttribute('aria-activedescendant', options[1].id);

    await user.keyboard('{ArrowUp}');
    expect(input).toHaveAttribute('aria-activedescendant', options[0].id);
  });

  it('Enter llama onSelect con el resaltado', async () => {
    const { user, onSelect } = setup();
    await typeAndWait(user, SHARED_NAME_TEXT);
    const expected = PLAYERS.filter((player) => player.name.includes(SHARED_NAME_TEXT))[1];

    await user.keyboard('{ArrowDown}{Enter}');

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(expected);
    expect(screen.getByRole('combobox')).toHaveValue('');
  });

  it('Escape cierra la lista y deja el foco en el input', async () => {
    const { user, input } = setup();
    await typeAndWait(user, 'mbappe');
    expect(screen.getByRole('listbox')).toBeInTheDocument();

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(input).toHaveFocus();
  });

  it('un clic en una sugerencia llama onSelect', async () => {
    const { user, onSelect } = setup();
    await typeAndWait(user, 'mbappe');

    await user.click(screen.getByRole('option'));

    expect(onSelect).toHaveBeenCalledWith(PLAYERS[0]);
  });

  it('mientras carga muestra "el DT está pensando..."', async () => {
    const { user } = setup('loading');

    await typeAndWait(user, 'mbappe');

    expect(screen.getByText('el DT está pensando...')).toBeInTheDocument();
  });

  it('con error muestra "reintentar" dentro del panel y lo llama', async () => {
    const { user, onRetry } = setup('error');
    await typeAndWait(user, 'mbappe');

    await user.click(screen.getByRole('button', { name: 'reintentar' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
