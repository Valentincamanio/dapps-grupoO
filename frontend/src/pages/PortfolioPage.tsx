// Portfolio del usuario de la sesión (FR-067): sus posiciones y los totales. Sin fecha, el actual,
// con la cotización vigente, el saldo libre y el reservado. Con una fecha en la dirección (`date`,
// AAAA-MM-DD), el de ese día (US7): valuado con la cotización de entonces y con solo el saldo total.
// Solo para usuarios comunes: el administrador no opera.
import { useId } from 'react';
import { useSearchParams } from 'react-router';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { PortfolioTotals } from '../components/PortfolioTotals';
import { PositionTable } from '../components/PositionTable';
import { usePortfolio } from '../hooks/usePortfolio';
import { formatDay } from '../utils/quotes';
import styles from './PortfolioPage.module.css';

// Argentina, UTC−3 sin horario de verano
const ARGENTINA_OFFSET_MS = 3 * 60 * 60 * 1000;

function todayInArgentina(): string {
  return new Date(Date.now() - ARGENTINA_OFFSET_MS).toISOString().slice(0, 10);
}

export function PortfolioPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  // La fecha va al servidor tal como está en la dirección: él la valida y explica el rechazo
  const date = searchParams.get('date') || undefined;
  const { status, portfolio, error, retry } = usePortfolio(date);
  const dateId = useId();

  function changeDate(next: string | undefined) {
    const params = new URLSearchParams();
    if (next) params.set('date', next);
    setSearchParams(params, { replace: false });
  }

  function renderContent() {
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading" />;
    if (status === 'error' || !portfolio) {
      // Una fecha rechazada (inválida, futura o anterior al registro) se dice con el mensaje del servidor
      if (date !== undefined && error?.status === 400) {
        return (
          <ChalkNote variant="error" action={{ label: 'volver al actual', onClick: () => changeDate(undefined) }}>
            {error.message}
          </ChalkNote>
        );
      }
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: retry }}>
          no se pudo cargar el portfolio
        </ChalkNote>
      );
    }

    const caption = portfolio.date
      ? `Tus posiciones al ${formatDay(portfolio.date)}, valuadas con la cotización de ese día`
      : 'Tus posiciones, valuadas con la cotización vigente';
    return (
      <>
        <PositionTable positions={portfolio.positions} caption={caption} />
        <PortfolioTotals
          totals={portfolio.totals}
          title={portfolio.date ? `Totales al ${formatDay(portfolio.date)}` : undefined}
        />
      </>
    );
  }

  return (
    <section className={styles.portfolio}>
      <ChalkText as="h1" variant="title">
        Portfolio
      </ChalkText>
      <div className={styles.dateBar}>
        <div className={styles.field}>
          <label htmlFor={dateId}>Ver el portfolio de una fecha</label>
          <input
            id={dateId}
            type="date"
            value={date ?? ''}
            max={todayInArgentina()}
            onChange={(event) => changeDate(event.target.value || undefined)}
          />
        </div>
        {date ? <ChalkButton onClick={() => changeDate(undefined)}>volver al actual</ChalkButton> : null}
      </div>
      {renderContent()}
    </section>
  );
}
