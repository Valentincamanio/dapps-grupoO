import { describe, expect, it } from 'vitest';
import type { Player } from '../../src/types/api';
import {
  addWeeks,
  boostCandidates,
  boostMeterWidth,
  boostWeekRange,
  boostWeekStatusLabel,
  formatBoostPercent,
  formatMultiplier,
  relativeWeekLabel,
  timeLeft,
} from '../../src/utils/boosts';

const PLAYERS: Player[] = [
  { id: 1, name: 'Lamine Yamal', position: 'FORWARD', team: 'FC Barcelona', league: 'LA_LIGA' },
  { id: 2, name: 'Bukayo Saka', position: 'FORWARD', team: 'Arsenal FC', league: 'PREMIER' },
  { id: 3, name: 'Gavi Páez', position: 'MIDFIELDER', team: 'FC Barcelona', league: 'LA_LIGA' },
];

describe('formatMultiplier', () => {
  it.each([
    [200, '×2'],
    [250, '×2,5'],
    [225, '×2,25'],
    [400, '×4'],
  ])('%i %% es %s', (percent, text) => {
    const multiplier = formatMultiplier(percent);

    expect(multiplier).toBe(text);
  });

  it('el porcentaje se muestra con el signo separado', () => {
    const percent = formatBoostPercent(300);

    expect(percent).toBe('300 %');
  });
});

describe('boostMeterWidth', () => {
  it('el máximo llena la barra y el mínimo, la mitad', () => {
    const full = boostMeterWidth(400);
    const half = boostMeterWidth(200);

    expect(full).toBe(100);
    expect(half).toBe(50);
  });
});

describe('semanas', () => {
  it('el rango usa el día y el mes abreviado, también entre meses', () => {
    const range = boostWeekRange('2026-10-26', '2026-11-01');

    expect(range).toBe('26 oct – 1 nov');
  });

  it('suma y resta semanas aunque cambie el mes o el año', () => {
    const next = addWeeks('2026-12-28', 1);
    const previous = addWeeks('2026-11-02', -1);

    expect(next).toBe('2027-01-04');
    expect(previous).toBe('2026-10-26');
  });

  it.each([
    [0, 'esta semana'],
    [1, 'próxima semana'],
    [-1, 'semana pasada'],
    [3, 'en 3 semanas'],
    [-2, 'hace 2 semanas'],
  ])('a %i semanas de la en curso se llama "%s"', (offset, label) => {
    const text = relativeWeekLabel(offset);

    expect(text).toBe(label);
  });

  it('el estado distingue la en curso, la programada, la cerrada y la que no tiene potenciados', () => {
    const labels = [
      boostWeekStatusLabel('CURRENT', true),
      boostWeekStatusLabel('UPCOMING', true),
      boostWeekStatusLabel('CLOSED', true),
      boostWeekStatusLabel('UPCOMING', false),
      boostWeekStatusLabel('CLOSED', false),
    ];

    expect(labels).toEqual(['en curso', 'programada', 'cerrada', 'sin elegir', 'cerrada']);
  });
});

describe('timeLeft', () => {
  const now = Date.parse('2026-10-31T10:00:00Z');

  it('muestra días, horas y minutos hasta el cierre', () => {
    const left = timeLeft('2026-11-01T22:49:30Z', now);

    expect(left).toBe('1d 12h 49m');
  });

  it('sin días muestra horas y minutos, y sin horas solo minutos', () => {
    const hours = timeLeft('2026-10-31T13:05:00Z', now);
    const minutes = timeLeft('2026-10-31T10:12:00Z', now);

    expect(hours).toBe('3h 5m');
    expect(minutes).toBe('12m');
  });

  it('pasado el cierre queda en cero', () => {
    const left = timeLeft('2026-10-31T09:00:00Z', now);

    expect(left).toBe('0m');
  });
});

describe('boostCandidates', () => {
  it('busca por nombre o equipo sin distinguir mayúsculas ni tildes', () => {
    const byName = boostCandidates(PLAYERS, 'PAEZ', null);
    const byTeam = boostCandidates(PLAYERS, 'barcelona', null);

    expect(byName.map((player) => player.id)).toEqual([3]);
    expect(byTeam.map((player) => player.id)).toEqual([1, 3]);
  });

  it('filtra por liga y sin texto devuelve todos los de esa liga', () => {
    const premier = boostCandidates(PLAYERS, '  ', 'PREMIER');

    expect(premier.map((player) => player.id)).toEqual([2]);
  });

  it('sin coincidencias devuelve una lista vacía', () => {
    const none = boostCandidates(PLAYERS, 'messi', null);

    expect(none).toEqual([]);
  });
});
