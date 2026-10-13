import { describe, expect, it } from 'vitest';
import type { Player } from '../../src/types/api';
import { PLAYERS, SHARED_NAME_TEXT } from '../mocks/players';
import { normalize, searchPlayers } from '../../src/utils/search';

describe('search', () => {
  it('normalize recorta, pasa a minúsculas y quita acentos', () => {
    expect(normalize('  Kylian MBAPPÉ ')).toBe('kylian mbappe');
  });

  it('una letra devuelve []', () => {
    expect(searchPlayers(PLAYERS, 'm')).toEqual([]);
  });

  it('" m " cuenta como una letra', () => {
    expect(searchPlayers(PLAYERS, ' m ')).toEqual([]);
  });

  it('una consulta vacía devuelve []', () => {
    expect(searchPlayers(PLAYERS, '')).toEqual([]);
  });

  it('"mbappe" encuentra "Kylian Mbappé"', () => {
    const found = searchPlayers(PLAYERS, 'mbappe');

    expect(found.map((player) => player.name)).toEqual(['Kylian Mbappé']);
  });

  it('mayúsculas y acentos son indistintos', () => {
    expect(searchPlayers(PLAYERS, 'MBAPPÉ')).toEqual(searchPlayers(PLAYERS, 'mbappe'));
    expect(searchPlayers(PLAYERS, 'kylian')).toHaveLength(1);
  });

  it('devuelve como máximo 8', () => {
    const found = searchPlayers(PLAYERS, SHARED_NAME_TEXT);

    expect(PLAYERS.filter((player) => player.name.includes(SHARED_NAME_TEXT)).length).toBeGreaterThan(8);
    expect(found).toHaveLength(8);
  });

  it('respeta el orden del catálogo', () => {
    const found = searchPlayers(PLAYERS, SHARED_NAME_TEXT);
    const ids = found.map((player: Player) => player.id);

    expect(ids).toEqual([...ids].sort((a, b) => a - b));
    expect(ids[0]).toBe(PLAYERS.find((player) => player.name.includes(SHARED_NAME_TEXT))?.id);
  });

  it('sin coincidencias devuelve []', () => {
    expect(searchPlayers(PLAYERS, 'zzz')).toEqual([]);
  });
});
