import { describe, expect, it } from 'vitest';
import type { Player } from '../../src/types/api';
import { deriveTeams, teamBelongsToLeague } from '../../src/utils/teams';

function player(id: number, team: string, league: Player['league']): Player {
  return { id, name: `Jugador ${id}`, position: 'FORWARD', team, league };
}

const PLAYERS: Player[] = [
  player(1, 'Real Madrid', 'LA_LIGA'),
  player(2, 'Real Madrid', 'LA_LIGA'),
  player(3, 'Atlético de Madrid', 'LA_LIGA'),
  player(4, 'Arsenal', 'PREMIER'),
  player(5, 'Álvarez FC', 'PREMIER'),
  player(6, 'Zaragoza', 'LA_LIGA'),
];

describe('teams', () => {
  it('deriveTeams no repite equipos', () => {
    const teams = deriveTeams(PLAYERS, null);

    expect(teams.filter((team) => team === 'Real Madrid')).toHaveLength(1);
    expect(new Set(teams).size).toBe(teams.length);
  });

  it('deriveTeams ordena alfabéticamente en español, con acentos', () => {
    const teams = deriveTeams(PLAYERS, null);

    expect(teams).toEqual(['Álvarez FC', 'Arsenal', 'Atlético de Madrid', 'Real Madrid', 'Zaragoza']);
  });

  it('deriveTeams se acota por liga', () => {
    expect(deriveTeams(PLAYERS, 'PREMIER')).toEqual(['Álvarez FC', 'Arsenal']);
    expect(deriveTeams(PLAYERS, 'LA_LIGA')).toEqual(['Atlético de Madrid', 'Real Madrid', 'Zaragoza']);
  });

  it('deriveTeams de una liga sin jugadores es una lista vacía', () => {
    expect(deriveTeams(PLAYERS, 'SERIE_A')).toEqual([]);
  });

  it('teamBelongsToLeague es verdadero si el equipo juega en esa liga', () => {
    expect(teamBelongsToLeague(PLAYERS, 'Real Madrid', 'LA_LIGA')).toBe(true);
  });

  it('teamBelongsToLeague es falso si el equipo es de otra liga o no existe', () => {
    expect(teamBelongsToLeague(PLAYERS, 'Real Madrid', 'PREMIER')).toBe(false);
    expect(teamBelongsToLeague(PLAYERS, 'Inexistente', 'LA_LIGA')).toBe(false);
  });
});
