import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SidePanel } from '../../src/components/SidePanel';

function renderPanel() {
  const onClose = vi.fn();
  const user = userEvent.setup();
  const view = render(
    <SidePanel label="ficha" onClose={onClose}>
      <p>contenido</p>
    </SidePanel>,
  );
  return { onClose, user, ...view };
}

describe('SidePanel', () => {
  it('es un diálogo con nombre, y toma el foco al abrirse', () => {
    renderPanel();

    expect(screen.getByRole('dialog', { name: 'ficha' })).toHaveFocus();
    expect(screen.getByText('contenido')).toBeInTheDocument();
  });

  it('Escape lo cierra', async () => {
    const { onClose, user } = renderPanel();

    await user.keyboard('{Escape}');

    expect(onClose).toHaveBeenCalledOnce();
  });

  it('un Escape que ya usó un diálogo de adentro no lo cierra', () => {
    const { onClose } = renderPanel();
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
    event.preventDefault();

    document.body.dispatchEvent(event);

    expect(onClose).not.toHaveBeenCalled();
  });

  it('tocar afuera lo cierra', async () => {
    const { onClose, user } = renderPanel();

    await user.click(screen.getByRole('button', { name: 'cerrar el panel' }));

    expect(onClose).toHaveBeenCalledOnce();
  });

  it('bloquea el desplazamiento de la página mientras está abierto y lo devuelve al cerrarse', () => {
    const { unmount } = renderPanel();
    expect(document.body.style.overflow).toBe('hidden');

    unmount();

    expect(document.body.style.overflow).toBe('');
  });

  it('al cerrarse devuelve el foco a donde estaba', () => {
    const opener = document.createElement('button');
    document.body.append(opener);
    opener.focus();
    const { unmount } = renderPanel();
    expect(opener).not.toHaveFocus();

    unmount();

    expect(opener).toHaveFocus();
    opener.remove();
  });
});
