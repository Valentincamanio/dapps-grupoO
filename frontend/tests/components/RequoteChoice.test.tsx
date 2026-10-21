// Historia 8 de la 007: la elección de cuándo recotizar (FR-065): "la semana siguiente"
// preseleccionada y nada se envía hasta confirmar.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { DEFAULT_REQUOTE, RequoteChoice } from '../../src/components/RequoteChoice';
import type { RequoteTiming } from '../../src/types/quotes';

function Harness({ onConfirm, onCancel }: { onConfirm: (timing: RequoteTiming) => void; onCancel?: () => void }) {
  const [value, setValue] = useState<RequoteTiming>(DEFAULT_REQUOTE);
  return <RequoteChoice value={value} onChange={setValue} onConfirm={() => onConfirm(value)} onCancel={onCancel} />;
}

describe('RequoteChoice', () => {
  it('ofrece las dos opciones con su explicación y la semana siguiente preseleccionada', () => {
    render(<Harness onConfirm={vi.fn()} />);

    const now = screen.getByRole('radio', { name: 'Recotizar ahora' });
    const nextWeek = screen.getByRole('radio', { name: 'Recotizar la semana siguiente' });

    expect(nextWeek).toBeChecked();
    expect(now).not.toBeChecked();
    expect(now).toHaveAccessibleDescription(/recotiza en el acto la última semana cerrada/);
    expect(nextWeek).toHaveAccessibleDescription(/las cotizaciones actuales no cambian/);
  });

  it('elegir una opción no envía nada; se envía la elegida al confirmar', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<Harness onConfirm={onConfirm} />);

    await user.click(screen.getByRole('radio', { name: 'Recotizar ahora' }));
    expect(onConfirm).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Confirmar' }));
    expect(onConfirm).toHaveBeenCalledExactlyOnceWith('AHORA');
  });

  it('cancelar no confirma', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<Harness onConfirm={onConfirm} onCancel={onCancel} />);

    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(onCancel).toHaveBeenCalledOnce();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('se usa solo con teclado', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<Harness onConfirm={onConfirm} />);

    await user.tab();
    expect(screen.getByRole('radio', { name: 'Recotizar la semana siguiente' })).toHaveFocus();
    await user.keyboard('{ArrowUp}');
    expect(screen.getByRole('radio', { name: 'Recotizar ahora' })).toBeChecked();
    await user.tab();
    expect(screen.getByRole('button', { name: 'Confirmar' })).toHaveFocus();
    await user.keyboard('{Enter}');

    expect(onConfirm).toHaveBeenCalledExactlyOnceWith('AHORA');
  });

  it('sin onConfirm no muestra botones: lo confirma el formulario que la contiene', () => {
    render(<RequoteChoice value={DEFAULT_REQUOTE} onChange={vi.fn()} />);

    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.getAllByRole('radio')).toHaveLength(2);
  });
});
