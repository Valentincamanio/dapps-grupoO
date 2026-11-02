// Resumen de una sincronización terminada: cuándo terminó y, por liga, cómo le fue y qué cambió.
import type { StatsSyncReport, SyncReport } from '../types/sync';
import { leagueLabel } from '../utils/positions';
import { formatInstant } from '../utils/quotes';
import { catalogStatusLabel, countsText, snapshotLabel, statsStatusLabel } from '../utils/sync';
import styles from './SyncReportView.module.css';

export function CatalogSyncReportView({ report }: { report: SyncReport }) {
  const failed = report.leagues.filter((league) => league.status === 'FAILED').length;
  return (
    <section className={styles.report} aria-label="informe de la sincronización del catálogo">
      <p className={styles.meta}>
        <span>terminó el {formatInstant(report.finishedAt)}</span>
        {report.inactivationApplied ? (
          <span>
            {report.inactivatedPlayers.length === 1
              ? '1 jugador quedó inactivo'
              : `${report.inactivatedPlayers.length} jugadores quedaron inactivos`}
          </span>
        ) : null}
      </p>
      {failed > 0 ? (
        <p className={styles.warning} role="alert">
          {failed === 1 ? 'una liga falló' : `${failed} ligas fallaron`}: conservan lo que tenían
        </p>
      ) : null}
      <table>
        <caption className={styles.caption}>Resultado por liga</caption>
        <thead>
          <tr>
            <th scope="col">Liga</th>
            <th scope="col">Estado</th>
            <th scope="col">Jugadores</th>
            <th scope="col">Partidos</th>
          </tr>
        </thead>
        <tbody>
          {report.leagues.map((league) => (
            <tr key={league.league}>
              <th scope="row" data-label="Liga">
                {leagueLabel(league.league)}
              </th>
              <td data-label="Estado" className={league.status === 'FAILED' ? styles.bad : undefined}>
                {catalogStatusLabel(league.status)}
                {league.failureReason ? `: ${league.failureReason}` : ''}
              </td>
              <td data-label="Jugadores">{countsText(league.players)}</td>
              <td data-label="Partidos">{countsText(league.matches)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

export function StatsSyncReportView({ report }: { report: StatsSyncReport }) {
  return (
    <section className={styles.report} aria-label="informe de la sincronización de estadísticas">
      <p className={styles.meta}>
        <span>terminó el {formatInstant(report.finishedAt)}</span>
        <span>{snapshotLabel(report.snapshot)}</span>
      </p>
      {report.cut ? (
        <p className={styles.warning} role="alert">
          WhoScored bloqueó la lectura: lo guardado se conserva y lo que falta queda para la próxima
        </p>
      ) : null}
      <table>
        <caption className={styles.caption}>Resultado por liga</caption>
        <thead>
          <tr>
            <th scope="col">Liga</th>
            <th scope="col">Estado</th>
            <th scope="col">Del snapshot</th>
            <th scope="col">Leídos de WhoScored</th>
            <th scope="col">Salteados</th>
          </tr>
        </thead>
        <tbody>
          {report.leagues.map((league) => (
            <tr key={league.league}>
              <th scope="row" data-label="Liga">
                {leagueLabel(league.league)}
              </th>
              <td data-label="Estado" className={league.status === 'PROCESSED' ? undefined : styles.bad}>
                {statsStatusLabel(league.status)}
              </td>
              <td data-label="Del snapshot">{league.matchesFromSnapshot}</td>
              <td data-label="Leídos de WhoScored">{league.matchesScraped}</td>
              <td data-label="Salteados">{league.skippedMatches.length}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
