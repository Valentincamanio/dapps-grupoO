import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { RankingList } from '../../src/components/RankingList';
import { leagueLabel } from '../../src/utils/positions';
import { PLAYER_WITH_ONLY_INITIAL_ID, RANKING_ROWS } from '../mocks/quotes';

const ROWS = RANKING_ROWS.slice(0, 3);

function bodyRows(): HTMLElement[] {
  return within(screen.getByRole('table')).getAllByRole('row').slice(1);
}

describe('RankingList', () => {
  it('muestra una fila por jugador, numerada desde el puesto de la primera fila', () => {
    render(<RankingList rows={ROWS} firstRank={11} playerHref={(id) => `/jugadores/${id}`} />);

    const rows = bodyRows();

    expect(rows).toHaveLength(ROWS.length);
    expect(within(rows[0]).getAllByRole('cell')[0]).toHaveTextContent('11');
    expect(within(rows[2]).getAllByRole('cell')[0]).toHaveTextContent('13');
  });

  it('cada fila tiene jugador con enlace a su ficha, equipo, liga, cotización y variación', () => {
    const [first] = ROWS;
    render(<RankingList rows={ROWS} firstRank={1} playerHref={(id) => `/jugadores/${id}`} />);

    const row = bodyRows()[0];

    expect(within(row).getByRole('link', { name: first.name })).toHaveAttribute(
      'href',
      `/jugadores/${first.playerId}`,
    );
    expect(row).toHaveTextContent(first.team);
    expect(row).toHaveTextContent(leagueLabel(first.league));
    expect(row).toHaveTextContent(/\d+,\d{2} cr\./);
    expect(row).toHaveTextContent(/(sube|baja|sin cambios)/);
  });

  it('cada fila dice la estrategia y la versión usadas', () => {
    render(<RankingList rows={ROWS} firstRank={1} playerHref={(id) => `/jugadores/${id}`} />);

    bodyRows().forEach((row, index) => {
      const { strategy } = ROWS[index].quote;
      expect(row).toHaveTextContent(`${strategy.name}, versión ${strategy.version}`);
    });
  });

  it('un jugador con solo la cotización inicial dice "sin variación"', () => {
    const initialRow = RANKING_ROWS.find((row) => row.playerId === PLAYER_WITH_ONLY_INITIAL_ID)!;
    render(<RankingList rows={[initialRow]} firstRank={1} playerHref={(id) => `/jugadores/${id}`} />);

    expect(bodyRows()[0]).toHaveTextContent('sin variación');
  });

  it('los únicos accionables son los enlaces a las fichas, sin listas', () => {
    const { container } = render(
      <RankingList rows={ROWS} firstRank={1} playerHref={(id) => `/jugadores/${id}`} />,
    );

    expect(screen.getAllByRole('link')).toHaveLength(ROWS.length);
    expect(screen.queryAllByRole('button')).toHaveLength(0);
    expect(container.querySelector('ul, ol')).toBeNull();
  });
});
