import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { EMPTY_RANGE_TEXT, QuoteChart } from '../../src/components/QuoteChart';
import { QuoteTable } from '../../src/components/QuoteTable';
import { lastPerWeek } from '../../src/utils/quotes';
import { quoteHistoryOf, REQUOTED_PLAYER_ID, STRATEGY_A, STRATEGY_B, WEEKS } from '../mocks/quotes';

// Inicial, calculadas y una semana recotizada
const HISTORY = quoteHistoryOf(REQUOTED_PLAYER_ID);

function pointButtons(): HTMLElement[] {
  return screen.getAllByRole('button', { name: /^Cotización de la semana del / });
}

describe('QuoteChart', () => {
  it('muestra un botón por semana, aunque la semana tenga varias cotizaciones', () => {
    render(<QuoteChart quotes={HISTORY} />);

    const buttons = pointButtons();

    expect(buttons).toHaveLength(WEEKS.length);
    expect(buttons[0]).toHaveAccessibleName('Cotización de la semana del 17/08/2026');
    expect(buttons[WEEKS.length - 1]).toHaveAccessibleName('Cotización de la semana del 05/10/2026');
  });

  it('el SVG es decorativo', () => {
    const { container } = render(<QuoteChart quotes={HISTORY} />);

    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });

  it('con el teclado se llega a cada punto y al enfocarlo muestra valor, semana, tipo y estrategia', async () => {
    const user = userEvent.setup();
    render(<QuoteChart quotes={HISTORY} />);

    await user.tab();

    expect(pointButtons()[0]).toHaveFocus();
    // La semana recotizada muestra la recotizada (la última de esa semana)
    expect(screen.getByText(/^Cotización de la semana del 17\/08\/2026:/)).toHaveTextContent(
      `104,00 cr., recotizada, estrategia ${STRATEGY_B.name}, versión ${STRATEGY_B.version}`,
    );

    await user.tab();

    expect(pointButtons()[1]).toHaveFocus();
    expect(screen.getByText(/^Cotización de la semana del 24\/08\/2026:/)).toHaveTextContent(
      `101,00 cr., calculada, estrategia ${STRATEGY_A.name}, versión ${STRATEGY_A.version}`,
    );
  });

  it('pasar el mouse o activar un punto también muestra su detalle', async () => {
    const user = userEvent.setup();
    render(<QuoteChart quotes={HISTORY} />);
    const buttons = pointButtons();

    await user.hover(buttons[2]);
    expect(screen.getByText(/^Cotización de la semana del 31\/08\/2026:/)).toBeInTheDocument();

    await user.click(buttons[3]);
    expect(screen.getByText(/^Cotización de la semana del 07\/09\/2026:/)).toBeInTheDocument();
  });

  it('con un solo punto se dibuja sin romperse', () => {
    render(<QuoteChart quotes={HISTORY.slice(0, 1)} />);

    expect(pointButtons()).toHaveLength(1);
  });

  it('un rango sin puntos muestra el mensaje en lugar del gráfico', () => {
    const { container } = render(<QuoteChart quotes={[]} />);

    expect(screen.getByRole('status')).toHaveTextContent(EMPTY_RANGE_TEXT);
    expect(container.querySelector('svg')).toBeNull();
    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });
});

describe('QuoteTable', () => {
  it('la tabla alternativa tiene los mismos puntos que el gráfico', async () => {
    const user = userEvent.setup();
    render(
      <>
        <QuoteChart quotes={HISTORY} />
        <QuoteTable quotes={HISTORY} />
      </>,
    );

    await user.click(screen.getByText('ver la tabla de cotizaciones por semana'));
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);

    const chartWeeks = pointButtons().map((button) =>
      button.getAttribute('aria-label')?.replace('Cotización de la semana del ', ''),
    );
    const tableWeeks = rows.map((row) => within(row).getByRole('rowheader').textContent);
    expect(tableWeeks).toEqual(chartWeeks);
    expect(rows).toHaveLength(lastPerWeek(HISTORY).length);
    expect(rows[0]).toHaveTextContent(`104,00 cr.+4,00 (+4,00 %) suberecotizada${STRATEGY_B.name}`);
  });

  it('sin puntos no muestra la tabla', () => {
    const { container } = render(<QuoteTable quotes={[]} />);

    expect(container).toBeEmptyDOMElement();
  });
});
