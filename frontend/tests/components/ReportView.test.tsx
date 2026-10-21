// Historia 8 de la 007: el informe del recálculo (FR-067).
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ReportView } from '../../src/components/ReportView';
import { pendingReasonLabel } from '../../src/utils/metrics';
import { weekLabel } from '../../src/utils/quotes';
import {
  ACTIVE_STRATEGY,
  EMPTY_RECALCULATION_REPORT,
  QUOTED_PLAYERS,
  RECALCULATION_REPORT,
  requotationReport,
  STRATEGY_B,
  WEEKS,
} from '../mocks/quotes';

function bodyRows(): HTMLElement[] {
  return within(screen.getByRole('table')).getAllByRole('row').slice(1);
}

describe('ReportView', () => {
  it('muestra el origen, la estrategia y cada semana cotizada con iniciales, calculadas y recotizadas', () => {
    render(<ReportView report={RECALCULATION_REPORT} />);

    expect(screen.getByText('origen: disparo manual')).toBeInTheDocument();
    expect(
      screen.getByText(`estrategia: ${ACTIVE_STRATEGY.name}, versión ${ACTIVE_STRATEGY.version}`),
    ).toBeInTheDocument();
    const rows = bodyRows();
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByRole('rowheader')).toHaveTextContent(weekLabel(WEEKS[6]));
    expect(within(rows[0]).getAllByRole('cell').map((cell) => cell.textContent)).toEqual([
      '0',
      String(QUOTED_PLAYERS),
      '0',
    ]);
    expect(screen.queryByText('No había semanas para cotizar')).toBeNull();
  });

  it('lista las semanas pendientes con su motivo traducido', () => {
    render(<ReportView report={RECALCULATION_REPORT} />);

    const pending = screen.getByRole('heading', { name: 'Semanas pendientes' }).parentElement as HTMLElement;
    const items = within(pending).getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent(`${weekLabel('2026-10-12')}: ${pendingReasonLabel('MISSING_STATS')}`);
    expect(items[1]).toHaveTextContent(pendingReasonLabel('PREVIOUS_WEEK_PENDING'));
  });

  it('un informe vacío dice que no había semanas para cotizar, sin tabla', () => {
    render(<ReportView report={EMPTY_RECALCULATION_REPORT} />);

    expect(screen.getByText('No había semanas para cotizar')).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Semanas pendientes' })).toBeNull();
  });

  it('el de una recotización cuenta las recotizadas', () => {
    render(<ReportView report={requotationReport(STRATEGY_B)} title="informe de la recotización" />);

    expect(screen.getByRole('heading', { name: 'informe de la recotización' })).toBeInTheDocument();
    expect(screen.getByText('origen: cambio de estrategia')).toBeInTheDocument();
    expect(within(bodyRows()[0]).getAllByRole('cell')[2]).toHaveTextContent(String(QUOTED_PLAYERS));
  });

  it('avisa si la corrida se cortó', () => {
    render(<ReportView report={{ ...RECALCULATION_REPORT, interruptedAt: WEEKS[7] }} />);

    expect(screen.getByRole('alert')).toHaveTextContent(`se cortó al guardar la ${weekLabel(WEEKS[7])}`);
  });
});
