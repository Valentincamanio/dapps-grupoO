import { describe, expect, it } from 'vitest';
import {
  SHEET_SIZE,
  describeEmptyCombination,
  parseBoardFilters,
  toPlayerQuery,
  toSearchParams,
} from '../../src/utils/filters';

function parse(query: string) {
  return parseBoardFilters(new URLSearchParams(query));
}

describe('filters', () => {
  it('sin parámetros devuelve los filtros vacíos en la primera hoja', () => {
    const filters = parse('');

    expect(filters).toEqual({ position: null, league: null, team: null, page: 1 });
  });

  it('lee posición, liga, equipo y hoja válidos', () => {
    const filters = parse('position=DEFENDER&league=LA_LIGA&team=Real%20Madrid&page=3');

    expect(filters).toEqual({
      position: 'DEFENDER',
      league: 'LA_LIGA',
      team: 'Real Madrid',
      page: 3,
    });
  });

  it('ignora una posición inválida', () => {
    const filters = parse('position=XYZ');

    expect(filters.position).toBeNull();
  });

  it('ignora una liga inválida', () => {
    const filters = parse('league=XYZ');

    expect(filters.league).toBeNull();
  });

  it.each(['abc', '0', '-3', '1.5'])('ignora la hoja %s y usa la primera', (page) => {
    const filters = parse(`page=${page}`);

    expect(filters.page).toBe(1);
  });

  it('recorta los espacios del equipo', () => {
    const filters = parse('team=%20%20Arsenal%20%20');

    expect(filters.team).toBe('Arsenal');
  });

  it('ignora un equipo vacío o de solo espacios', () => {
    const filters = parse('team=%20%20');

    expect(filters.team).toBeNull();
  });

  it('serializa los filtros con valor', () => {
    const params = toSearchParams({
      position: 'FORWARD',
      league: 'SERIE_A',
      team: 'Inter',
      page: 2,
    });

    expect(params.toString()).toBe('position=FORWARD&league=SERIE_A&team=Inter&page=2');
  });

  it('omite la hoja 1 y los filtros vacíos al serializar', () => {
    const params = toSearchParams({ position: null, league: null, team: null, page: 1 });

    expect(params.toString()).toBe('');
  });

  it('toPlayerQuery resta 1 a la hoja y fija el tamaño en 12', () => {
    const query = toPlayerQuery({ position: 'DEFENDER', league: null, team: null, page: 3 });

    expect(query).toEqual({
      position: 'DEFENDER',
      league: undefined,
      team: undefined,
      page: 2,
      size: SHEET_SIZE,
    });
    expect(SHEET_SIZE).toBe(12);
  });
});

describe('describeEmptyCombination', () => {
  const none = { position: null, league: null, team: null, page: 1 };

  it('solo posición', () => {
    expect(describeEmptyCombination({ ...none, position: 'DEFENDER' })).toBe(
      'no hay defensores en la pizarra',
    );
  });

  it('posición + equipo', () => {
    expect(describeEmptyCombination({ ...none, position: 'GOALKEEPER', team: 'Fulham' })).toBe(
      'no hay arqueros de ese equipo en la pizarra',
    );
  });

  it('posición + liga', () => {
    expect(describeEmptyCombination({ ...none, position: 'DEFENDER', league: 'LA_LIGA' })).toBe(
      'no hay defensores de La Liga en la pizarra',
    );
  });

  it('solo equipo', () => {
    expect(describeEmptyCombination({ ...none, team: 'Fulham' })).toBe(
      'no hay jugadores de ese equipo en la pizarra',
    );
  });

  it('solo liga', () => {
    expect(describeEmptyCombination({ ...none, league: 'SERIE_A' })).toBe(
      'no hay jugadores de Serie A en la pizarra',
    );
  });
});
