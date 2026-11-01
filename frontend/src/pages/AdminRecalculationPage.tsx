// Disparo manual del recálculo (FR-067): cotiza las semanas cerradas que falten con la estrategia
// activa y muestra el informe. Un 409 (otro recálculo o una sincronización en curso) muestra el
// motivo y deja la pantalla como estaba, con el último informe si había uno (FR-068).
//
// Debajo, las dos sincronizaciones manuales: el catálogo con Football-Data.org y las estadísticas
// con WhoScored, de las cinco ligas o de una. Hay a lo sumo una a la vez, así que mientras corre una
// la otra no se ofrece.
import { useState } from 'react';
import { Link } from 'react-router';
import { AdminNav } from '../components/AdminNav';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { ReportView } from '../components/ReportView';
import { SyncPanel } from '../components/SyncPanel';
import { CatalogSyncReportView, StatsSyncReportView } from '../components/SyncReportView';
import { useMutation } from '../hooks/useMutation';
import { useRecalculation } from '../hooks/useRecalculation';
import { syncCatalog, syncStats } from '../services/syncService';
import type { League } from '../types/api';
import styles from './AdminRecalculationPage.module.css';

export function AdminRecalculationPage() {
  const { status, report, error, isConflict, recalculate } = useRecalculation();
  const running = status === 'running';
  const catalog = useMutation((league: League | null) => syncCatalog(league ?? undefined));
  const stats = useMutation((league: League | null) => syncStats(league ?? undefined));
  const [catalogLeague, setCatalogLeague] = useState<League | null>(null);
  const [statsLeague, setStatsLeague] = useState<League | null>(null);
  const syncing = catalog.status === 'running' || stats.status === 'running';

  return (
    <section className={styles.admin}>
      <ChalkText as="h1" variant="title">
        Administración
      </ChalkText>
      <AdminNav current="recalculation" LinkComponent={Link} />

      <h2 className={styles.heading}>Recálculo</h2>
      <p className={styles.hint}>
        cotiza con la estrategia activa las semanas cerradas que todavía no tienen cotización; las
        que ya están cotizadas no se tocan
      </p>
      <div className={styles.actions}>
        <ChalkButton busy={running} onClick={() => void recalculate()}>
          Recalcular cotizaciones
        </ChalkButton>
      </div>

      {running ? <ChalkNote variant="loading">recalculando, puede tardar un rato...</ChalkNote> : null}
      {error && !running ? (
        <ChalkNote
          variant="error"
          action={isConflict ? undefined : { label: 'reintentar', onClick: () => void recalculate() }}
        >
          {error.message}
        </ChalkNote>
      ) : null}
      {report ? <ReportView report={report} /> : null}

      <h2 className={styles.heading}>Sincronización</h2>
      <p className={styles.hint}>
        se hace sola los lunes a las 04:00; acá se dispara a mano. Después de traer estadísticas nuevas,
        recalculá las cotizaciones para que las usen
      </p>
      <SyncPanel
        title="Catálogo (Football-Data.org)"
        description="Trae equipos, jugadores y partidos. Puede tardar hasta un minuto por el límite de consultas de la fuente."
        buttonLabel="Sincronizar catálogo"
        league={catalogLeague}
        onLeagueChange={setCatalogLeague}
        busy={catalog.status === 'running'}
        disabled={syncing}
        onSync={() => void catalog.run(catalogLeague)}
      >
        {catalog.status === 'running' ? <ChalkNote variant="loading">sincronizando el catálogo...</ChalkNote> : null}
        {catalog.error && catalog.status !== 'running' ? (
          <ChalkNote variant="error">{catalog.error.message}</ChalkNote>
        ) : null}
        {catalog.data ? <CatalogSyncReportView report={catalog.data} /> : null}
      </SyncPanel>
      <SyncPanel
        title="Estadísticas (WhoScored)"
        description="Lee las estadísticas de los partidos terminados. Lo que ya está en el snapshot carga enseguida; cada partido nuevo se lee de WhoScored y puede tardar varios minutos."
        buttonLabel="Sincronizar estadísticas"
        league={statsLeague}
        onLeagueChange={setStatsLeague}
        busy={stats.status === 'running'}
        disabled={syncing}
        onSync={() => void stats.run(statsLeague)}
      >
        {stats.status === 'running' ? (
          <ChalkNote variant="loading">sincronizando las estadísticas, puede tardar varios minutos...</ChalkNote>
        ) : null}
        {stats.error && stats.status !== 'running' ? (
          <ChalkNote variant="error">{stats.error.message}</ChalkNote>
        ) : null}
        {stats.data ? <StatsSyncReportView report={stats.data} /> : null}
      </SyncPanel>
    </section>
  );
}
