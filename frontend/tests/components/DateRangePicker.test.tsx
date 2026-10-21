import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { DateRangePicker } from '../../src/components/DateRangePicker';
import type { QuoteRange } from '../../src/types/quotes';

const TODAY = '2026-10-21';

function setup(value: QuoteRange = {}) {
  const onChange = vi.fn<(range: QuoteRange) => void>();
  const user = userEvent.setup();
  render(<DateRangePicker value={value} onChange={onChange} today={TODAY} />);
  return { onChange, user };
}

describe('DateRangePicker', () => {
  it('tiene dos fechas con etiqueta', () => {
    setup();

    expect(screen.getByLabelText('Desde')).toHaveAttribute('type', 'date');
    expect(screen.getByLabelText('Hasta')).toHaveAttribute('type', 'date');
  });

  it('"Últimas 8 semanas" devuelve el rango de las últimas 8 semanas hasta hoy', async () => {
    const { onChange, user } = setup();

    await user.click(screen.getByRole('button', { name: 'Últimas 8 semanas' }));

    expect(onChange).toHaveBeenLastCalledWith({ from: '2026-08-26', to: TODAY });
    expect(screen.getByLabelText('Desde')).toHaveValue('2026-08-26');
    expect(screen.getByLabelText('Hasta')).toHaveValue(TODAY);
    expect(screen.getByRole('button', { name: 'Últimas 8 semanas' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('"Últimas 26 semanas" devuelve el rango de las últimas 26 semanas hasta hoy', async () => {
    const { onChange, user } = setup();

    await user.click(screen.getByRole('button', { name: 'Últimas 26 semanas' }));

    expect(onChange).toHaveBeenLastCalledWith({ from: '2026-04-22', to: TODAY });
  });

  it('"Todo el historial" quita los dos extremos y viene marcado sin rango', async () => {
    const { onChange, user } = setup({ from: '2026-09-01', to: '2026-09-30' });
    const all = screen.getByRole('button', { name: 'Todo el historial' });
    expect(all).toHaveAttribute('aria-pressed', 'false');

    await user.click(all);

    expect(onChange).toHaveBeenLastCalledWith({});
    expect(all).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByLabelText('Desde')).toHaveValue('');
  });

  it('elegir las fechas devuelve el rango elegido', async () => {
    const { onChange, user } = setup();

    await user.type(screen.getByLabelText('Desde'), '2026-09-01');
    await user.type(screen.getByLabelText('Hasta'), '2026-09-30');

    expect(onChange).toHaveBeenLastCalledWith({ from: '2026-09-01', to: '2026-09-30' });
  });

  it('un rango invertido muestra el error y no se devuelve', async () => {
    const { onChange, user } = setup({ from: '2026-09-30' });

    await user.type(screen.getByLabelText('Hasta'), '2026-09-01');

    expect(screen.getByRole('alert')).toHaveTextContent(/no puede ser posterior/);
    expect(screen.getByLabelText('Hasta')).toHaveAttribute('aria-invalid', 'true');
    expect(onChange).not.toHaveBeenCalledWith({ from: '2026-09-30', to: '2026-09-01' });
  });
});
