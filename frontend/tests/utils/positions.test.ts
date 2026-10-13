import { describe, expect, it } from 'vitest';
import { LEAGUES, POSITIONS, ROLES } from '../../src/types/api';
import type { PitchZone } from '../../src/types/board';
import {
  leagueLabel,
  positionLabel,
  positionPlural,
  positionToZone,
  roleLabel,
  zoneLabel,
  zoneToPosition,
} from '../../src/utils/positions';

const ZONES: PitchZone[] = ['GOAL', 'DEFENSE', 'MIDFIELD', 'ATTACK'];

describe('positions', () => {
  it.each(ZONES)('la zona %s vuelve igual después de ir y volver por posición', (zone) => {
    const position = zoneToPosition(zone);

    expect(position).not.toBeNull();
    expect(positionToZone(position)).toBe(zone);
  });

  it('cada posición vuelve igual después de ir y volver por zona', () => {
    const roundTrips = POSITIONS.map((position) => zoneToPosition(positionToZone(position)));

    expect(roundTrips).toEqual([...POSITIONS]);
  });

  it('mapea las cuatro zonas a las cuatro posiciones', () => {
    const positions = ZONES.map((zone) => zoneToPosition(zone));

    expect(positions).toEqual(['GOALKEEPER', 'DEFENDER', 'MIDFIELDER', 'FORWARD']);
  });

  it('"todos" no es una zona ni una posición', () => {
    expect(zoneToPosition(null)).toBeNull();
    expect(positionToZone(null)).toBeNull();
  });

  it('las posiciones tienen etiqueta en español', () => {
    const labels = POSITIONS.map((position) => positionLabel(position));

    expect(labels).toEqual(['Arquero', 'Defensor', 'Mediocampista', 'Delantero']);
  });

  it('las zonas tienen etiqueta en español', () => {
    const labels = ZONES.map((zone) => zoneLabel(zone));

    expect(labels).toEqual(['arco', 'defensa', 'mediocampo', 'delantera']);
  });

  it('las ligas tienen etiqueta en español', () => {
    const labels = LEAGUES.map((league) => leagueLabel(league));

    expect(labels).toEqual(['Premier League', 'Bundesliga', 'La Liga', 'Serie A', 'Ligue 1']);
  });

  it('los roles tienen etiqueta en español', () => {
    const labels = ROLES.map((role) => roleLabel(role));

    expect(labels).toEqual(['Usuario', 'Administrador']);
  });

  it('los plurales de posición hablan de "jugadores" cuando no hay posición', () => {
    const plurals = [...POSITIONS, null].map((position) => positionPlural(position));

    expect(plurals).toEqual(['arqueros', 'defensores', 'mediocampistas', 'delanteros', 'jugadores']);
  });
});
