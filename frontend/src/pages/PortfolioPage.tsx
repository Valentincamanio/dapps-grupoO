// Portfolio actual del usuario de la sesión (FR-067): sus posiciones con la cotización vigente y los
// totales con el saldo libre y el reservado. Solo para usuarios comunes: el administrador no opera.
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { PortfolioTotals } from '../components/PortfolioTotals';
import { PositionTable } from '../components/PositionTable';
import { usePortfolio } from '../hooks/usePortfolio';
import styles from './PortfolioPage.module.css';

export function PortfolioPage() {
  const { status, portfolio, retry } = usePortfolio();

  function renderContent() {
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading" />;
    if (status === 'error' || !portfolio) {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: retry }}>
          no se pudo cargar el portfolio
        </ChalkNote>
      );
    }

    return (
      <>
        <PositionTable
          positions={portfolio.positions}
          caption="Tus posiciones, valuadas con la cotización vigente"
        />
        <PortfolioTotals totals={portfolio.totals} />
      </>
    );
  }

  return (
    <section className={styles.portfolio}>
      <ChalkText as="h1" variant="title">
        Portfolio
      </ChalkText>
      {renderContent()}
    </section>
  );
}
