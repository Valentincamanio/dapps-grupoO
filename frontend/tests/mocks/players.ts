// Catálogo de prueba: 55 jugadores, así hay varias hojas de 12 y el catálogo completo
// necesita dos pedidos de size=50.
import type { League, Player, Position } from '../../src/types/api';

interface TeamSeed {
  name: string;
  league: League;
}

// Tres equipos por liga, las cinco ligas
export const TEAMS: TeamSeed[] = [
  { name: 'Manchester City', league: 'PREMIER' },
  { name: 'Arsenal', league: 'PREMIER' },
  { name: 'Fulham', league: 'PREMIER' },
  { name: 'Bayern Múnich', league: 'BUNDESLIGA' },
  { name: 'Borussia Dortmund', league: 'BUNDESLIGA' },
  { name: 'Bayer Leverkusen', league: 'BUNDESLIGA' },
  { name: 'Real Madrid', league: 'LA_LIGA' },
  { name: 'FC Barcelona', league: 'LA_LIGA' },
  { name: 'Atlético de Madrid', league: 'LA_LIGA' },
  { name: 'Inter', league: 'SERIE_A' },
  { name: 'Juventus', league: 'SERIE_A' },
  { name: 'Napoli', league: 'SERIE_A' },
  { name: 'Paris Saint-Germain', league: 'LIGUE_1' },
  { name: 'Olympique de Marsella', league: 'LIGUE_1' },
  { name: 'AS Mónaco', league: 'LIGUE_1' },
];

// Equipo sin arqueros, para probar la combinación vacía
export const TEAM_WITHOUT_GOALKEEPERS = 'Fulham';

// Texto compartido por más de 8 nombres, para probar el tope de sugerencias del buscador
export const SHARED_NAME_TEXT = 'Silva';

const POSITION_CYCLE: Position[] = ['GOALKEEPER', 'DEFENDER', 'MIDFIELDER', 'FORWARD'];

const FIRST_NAMES = [
  'Álvaro',
  'Bruno',
  'Camilo',
  'Diego',
  'Emiliano',
  'Facundo',
  'Gonzalo',
  'Héctor',
  'Iván',
  'Joaquín',
  'Lucas',
];
const LAST_NAMES = ['Núñez', 'Peña', 'Ibáñez', 'Gómez', 'Fernández', 'Muñoz', 'Rojas', 'Vidal'];

function buildPlayer(index: number): Player {
  const id = index + 1;
  if (id === 1) {
    return { id, name: 'Kylian Mbappé', position: 'FORWARD', team: 'Real Madrid', league: 'LA_LIGA' };
  }
  const team = TEAMS[index % TEAMS.length];
  let position = POSITION_CYCLE[index % POSITION_CYCLE.length];
  if (team.name === TEAM_WITHOUT_GOALKEEPERS && position === 'GOALKEEPER') {
    position = 'DEFENDER';
  }
  // Cada quinto jugador comparte el apellido "Silva"
  const lastName = index % 5 === 0 ? SHARED_NAME_TEXT : LAST_NAMES[index % LAST_NAMES.length];
  const name = `${FIRST_NAMES[index % FIRST_NAMES.length]} ${lastName}`;
  return { id, name, position, team: team.name, league: team.league };
}

export const PLAYERS: Player[] = Array.from({ length: 55 }, (_, index) => buildPlayer(index));
