// Filtros del listado del mercado: tipo de orden, liga, equipo y tipo de usuario dueño, combinables.
// Las pestañas son las de la pizarra y el equipo usa TeamSelect tal cual; los grupos se parten en
// renglones a 360 px. Recibe los filtros y los callbacks: no pide nada a la API ni toca la dirección.
import type { League } from '../types/api';
import { LEAGUES } from '../types/api';
import type { MarketFilters as Filters, OrderSide, OwnerType } from '../types/market';
import { ORDER_SIDES, OWNER_TYPES } from '../types/market';
import { leagueLabel } from '../utils/positions';
import styles from './MarketFilters.module.css';
import { TapeTab } from './TapeTab';
import { TeamSelect } from './TeamSelect';

interface MarketFiltersProps {
  filters: Filters;
  // Los equipos que ofrece TeamSelect y el estado de su carga
  teams: string[];
  teamsStatus: 'idle' | 'loading' | 'success' | 'error';
  onTeamsOpen: () => void;
  onTeamsRetry: () => void;
  onSideChange: (side: OrderSide | null) => void;
  onLeagueChange: (league: League | null) => void;
  onTeamChange: (team: string | null) => void;
  onOwnerTypeChange: (ownerType: OwnerType | null) => void;
}

const SIDE_TABS: Record<OrderSide, string> = {
  BUY: 'compras',
  SELL: 'ventas',
};

const OWNER_TYPE_TABS: Record<OwnerType, string> = {
  USER: 'de usuarios',
  ADMIN: 'del administrador',
};

export function MarketFilters({
  filters,
  teams,
  teamsStatus,
  onTeamsOpen,
  onTeamsRetry,
  onSideChange,
  onLeagueChange,
  onTeamChange,
  onOwnerTypeChange,
}: MarketFiltersProps) {
  return (
    <div className={styles.filters}>
      <div className={styles.group} role="group" aria-label="filtrar por tipo de orden">
        <TapeTab label="todas" active={filters.side === null} onSelect={() => onSideChange(null)} />
        {ORDER_SIDES.map((side) => (
          <TapeTab
            key={side}
            label={SIDE_TABS[side]}
            active={filters.side === side}
            onSelect={() => onSideChange(side)}
          />
        ))}
      </div>
      <div className={styles.group} role="group" aria-label="filtrar por tipo de usuario dueño">
        <TapeTab label="todos" active={filters.ownerType === null} onSelect={() => onOwnerTypeChange(null)} />
        {OWNER_TYPES.map((ownerType) => (
          <TapeTab
            key={ownerType}
            label={OWNER_TYPE_TABS[ownerType]}
            active={filters.ownerType === ownerType}
            onSelect={() => onOwnerTypeChange(ownerType)}
          />
        ))}
      </div>
      <div className={styles.group} role="group" aria-label="filtrar por liga">
        <TapeTab label="todas" active={filters.league === null} onSelect={() => onLeagueChange(null)} />
        {LEAGUES.map((league) => (
          <TapeTab
            key={league}
            label={leagueLabel(league)}
            active={filters.league === league}
            onSelect={() => onLeagueChange(league)}
          />
        ))}
      </div>
      <div className={styles.team}>
        <TeamSelect
          teams={teams}
          value={filters.team}
          status={teamsStatus}
          onOpen={onTeamsOpen}
          onSelect={onTeamChange}
          onRetry={onTeamsRetry}
        />
      </div>
    </div>
  );
}
