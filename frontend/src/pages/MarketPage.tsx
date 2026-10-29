// El mercado (/mercado): las órdenes con tokens pendientes, públicas, con los filtros y la hoja en la
// dirección (US5, FR-047 a FR-049). Nunca muestra quién es el dueño de una orden, solo su tipo.
import { Board } from '../components/Board';
import { ChalkNote } from '../components/ChalkNote';
import { MarketFilters } from '../components/MarketFilters';
import { OrderList } from '../components/OrderList';
import { Pager } from '../components/Pager';
import { useCatalog } from '../hooks/useCatalog';
import { toMarketQuery, useMarketFilters } from '../hooks/useMarketFilters';
import { useMarketOrders } from '../hooks/useMarketOrders';
import { deriveTeams } from '../utils/teams';
import styles from './MarketPage.module.css';

export function MarketPage() {
  const { filters, setPage, setSide, setLeague, setTeam, setOwnerType, clear } = useMarketFilters();
  const catalog = useCatalog();
  const { status, page, orders, reload } = useMarketOrders(toMarketQuery(filters));

  function renderContent() {
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading" />;
    if (status === 'error' || !page) {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: reload }}>
          no se pudo cargar el mercado
        </ChalkNote>
      );
    }
    if (page.totalElements === 0) {
      return (
        <ChalkNote variant="info" action={{ label: 'limpiar filtros', onClick: clear }}>
          no hay órdenes pendientes con esos filtros
        </ChalkNote>
      );
    }
    if (orders.length === 0) {
      return (
        <ChalkNote variant="info" action={{ label: 'volver a la primera hoja', onClick: () => setPage(1) }}>
          esta hoja del mercado está vacía
        </ChalkNote>
      );
    }

    return (
      <>
        <OrderList variant="market" orders={orders} />
        <Pager
          sheet={page.page + 1}
          totalSheets={page.totalPages}
          hasPrevious={page.hasPrevious}
          hasNext={page.hasNext}
          onPrevious={() => setPage(page.page)}
          onNext={() => setPage(page.page + 2)}
        />
      </>
    );
  }

  return (
    <Board>
      <div className={styles.content}>
        <h1 className={styles.title}>Mercado</h1>
        <MarketFilters
          filters={filters}
          teams={deriveTeams(catalog.players, filters.league)}
          teamsStatus={catalog.status}
          onTeamsOpen={() => void catalog.ensureLoaded()}
          onTeamsRetry={() => void catalog.retry()}
          onSideChange={setSide}
          onLeagueChange={(league) => void setLeague(league)}
          onTeamChange={setTeam}
          onOwnerTypeChange={setOwnerType}
        />
        {renderContent()}
      </div>
    </Board>
  );
}
