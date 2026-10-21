// Informe de una corrida del recálculo: con qué estrategia y por qué corrió, las semanas cotizadas
// con los jugadores de cotización inicial, calculada y recotizada, y las pendientes con su motivo.
// Por debajo de un ancho la tabla pasa a tarjetas (CSS).
import type { RecalculationReport } from '../types/quotes';
import { pendingReasonLabel, recalculationOriginLabel } from '../utils/metrics';
import { formatInstant, weekLabel } from '../utils/quotes';
import styles from './ReportView.module.css';

interface ReportViewProps {
  report: RecalculationReport;
  // Por defecto "informe del recálculo"
  title?: string;
}

export function ReportView({ report, title = 'informe del recálculo' }: ReportViewProps) {
  return (
    <section className={styles.report} aria-label={title}>
      <h3 className={styles.title}>{title}</h3>
      <p className={styles.meta}>
        <span>origen: {recalculationOriginLabel(report.origin)}</span>
        <span>
          estrategia: {report.strategy.name}, versión {report.strategy.version}
        </span>
        <span>
          de {formatInstant(report.startedAt)} a {formatInstant(report.finishedAt)}
        </span>
      </p>

      {report.interruptedAt ? (
        <p className={styles.interrupted} role="alert">
          la corrida se cortó al guardar la {weekLabel(report.interruptedAt)}: esa semana y las
          siguientes quedan para la próxima
        </p>
      ) : null}

      {report.nothingToQuote ? (
        <p className={styles.empty}>No había semanas para cotizar</p>
      ) : null}

      {report.quotedWeeks.length > 0 ? (
        <table className={styles.table}>
          <caption className={styles.caption}>Semanas cotizadas</caption>
          <thead>
            <tr>
              <th scope="col">Semana</th>
              <th scope="col">Iniciales</th>
              <th scope="col">Calculadas</th>
              <th scope="col">Recotizadas</th>
            </tr>
          </thead>
          <tbody>
            {report.quotedWeeks.map((week) => (
              <tr key={week.weekStart}>
                <th scope="row" data-label="Semana" className={styles.week}>
                  {weekLabel(week.weekStart)}
                </th>
                <td data-label="Iniciales">{week.initial}</td>
                <td data-label="Calculadas">{week.calculated}</td>
                <td data-label="Recotizadas">{week.requoted}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      {report.pendingWeeks.length > 0 ? (
        <div className={styles.pending}>
          <h4 className={styles.subtitle}>Semanas pendientes</h4>
          <ul className={styles.pendingList}>
            {report.pendingWeeks.map((week) => (
              <li key={week.weekStart}>
                <span className={styles.week}>{weekLabel(week.weekStart)}</span>:{' '}
                {pendingReasonLabel(week.reason)}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}
