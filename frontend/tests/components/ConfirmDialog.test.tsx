// Historias 1 y 3 de la 008: la confirmación de comprar o vender (FR-063). Nada se ejecuta hasta
// confirmar; el diálogo toma el foco, Enter confirma y Escape cancela.
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDialog } from '../../src/components/ConfirmDialog';

const TITLE = '¿Comprar 10 tokens?';

function renderDialog(busy = false) {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <ConfirmDialog title={TITLE} busy={busy} onConfirm={onConfirm} onCancel={onCancel}>
      <p>Total estimado: 10,21 cr.</p>
    </ConfirmDialog>,
  );
  return { onConfirm, onCancel, user: userEvent.setup() };
}

describe('ConfirmDialog', () => {
  it('se anuncia como diálogo con su título y su detalle', () => {
    renderDialog();

    const dialog = screen.getByRole('dialog', { name: TITLE });

    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveTextContent('Total estimado: 10,21 cr.');
  });

  it('toma el foco en confirmar y no ejecuta nada al abrirse', () => {
    const { onConfirm, onCancel } = renderDialog();

    const confirm = screen.getByRole('button', { name: 'Confirmar' });

    expect(confirm).toHaveFocus();
    expect(onConfirm).not.toHaveBeenCalled();
    expect(onCancel).not.toHaveBeenCalled();
  });

  it('confirmar ejecuta una sola vez', async () => {
    const { onConfirm, onCancel, user } = renderDialog();

    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(onConfirm).toHaveBeenCalledOnce();
    expect(onCancel).not.toHaveBeenCalled();
  });

  it('Enter confirma', async () => {
    const { onConfirm, onCancel, user } = renderDialog();

    await user.keyboard('{Enter}');

    expect(onConfirm).toHaveBeenCalledOnce();
    expect(onCancel).not.toHaveBeenCalled();
  });

  it('Enter con el foco en el detalle también confirma', () => {
    const { onConfirm } = renderDialog();

    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Enter' });

    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it('Escape cancela sin confirmar', async () => {
    const { onConfirm, onCancel, user } = renderDialog();

    await user.keyboard('{Escape}');

    expect(onCancel).toHaveBeenCalledOnce();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('Enter sobre cancelar cancela y no confirma', async () => {
    const { onConfirm, onCancel, user } = renderDialog();
    await user.tab();

    await user.keyboard('{Enter}');

    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();
    expect(onCancel).toHaveBeenCalledOnce();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('Tab y Shift+Tab dan la vuelta entre sus botones sin salir del diálogo', async () => {
    render(<button type="button">Comprar</button>);
    const { user } = renderDialog();
    const confirm = screen.getByRole('button', { name: 'Confirmar' });
    const cancel = screen.getByRole('button', { name: 'Cancelar' });

    await user.tab();
    expect(cancel).toHaveFocus();
    await user.tab();
    expect(confirm).toHaveFocus();
    await user.tab({ shift: true });
    expect(cancel).toHaveFocus();
  });

  it('mientras espera la respuesta no se puede confirmar de nuevo ni cancelar', () => {
    const { onConfirm, onCancel } = renderDialog(true);

    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Enter' });

    expect(screen.getByRole('button', { name: 'Confirmar' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled();
    expect(onCancel).not.toHaveBeenCalled();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('al cerrarse devuelve el foco a donde estaba', () => {
    render(<button type="button">Comprar</button>);
    const opener = screen.getByRole('button', { name: 'Comprar' });
    opener.focus();
    const { unmount } = render(<ConfirmDialog title={TITLE} onConfirm={vi.fn()} onCancel={vi.fn()} />);

    unmount();

    expect(opener).toHaveFocus();
  });
});
