// Disparo manual del recálculo (FR-067): cotiza las semanas cerradas que falten con la estrategia
// activa y muestra el informe. Un 409 (otro recálculo o una sincronización en curso) muestra el
// motivo y deja la pantalla como estaba, con el último informe si había uno (FR-068).
import { Link } from 'react-router';
import { AdminNav } from '../components/AdminNav';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { ReportView } from '../components/ReportView';
import { useRecalculation } from '../hooks/useRecalculation';
import styles from './AdminRecalculationPage.module.css';

export function AdminRecalculationPage() {
  const { status, report, error, isConflict, recalculate } = useRecalculation();
  const running = status === 'running';

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
    </section>
  );
}
