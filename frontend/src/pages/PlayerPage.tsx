// Ficha de un jugador: hoja de scout dentro del pizarrón, con su cotización y su evolución.
import { useId, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { Board } from '../components/Board';
import { ChalkNote } from '../components/ChalkNote';
import { DateRangePicker } from '../components/DateRangePicker';
import { QuoteChart } from '../components/QuoteChart';
import { QuotePanel } from '../components/QuotePanel';
import { QuoteTable } from '../components/QuoteTable';
import { ScoutSheet } from '../components/ScoutSheet';
import { usePlayer } from '../hooks/usePlayer';
import { usePlayerQuote } from '../hooks/usePlayerQuote';
import { useQuoteHistory } from '../hooks/useQuoteHistory';
import type { QuoteRange } from '../types/quotes';
import styles from './PlayerPage.module.css';

interface PlayerLocationState {
  boardSearch?: string;
}

// Aviso sobre el papel de la hoja (ChalkNote está pensado para el pizarrón)
interface SheetNoteProps {
  variant: 'info' | 'loading' | 'error';
  text: string;
  retry?: { label: string; onClick: () => void };
}

function SheetNote({ variant, text, retry }: SheetNoteProps) {
  return (
    <div
      className={`${styles.note} ${styles[variant]}`}
      role={variant === 'error' ? 'alert' : 'status'}
    >
      <span>{text}</span>
      {retry ? (
        <button type="button" className={styles.retry} onClick={retry.onClick}>
          {retry.label}
        </button>
      ) : null}
    </div>
  );
}

export function PlayerPage() {
  const { id } = useParams();
  const location = useLocation();
  const { status, player, notFound, retry } = usePlayer(id);
  // La cotización y la evolución se piden con el jugador ya cargado
  const quote = usePlayerQuote(player?.id);
  const [range, setRange] = useState<QuoteRange>({});
  const history = useQuoteHistory(player?.id, range);
  const quoteHeadingId = useId();
  const historyHeadingId = useId();

  // Vuelve a la pizarra con los mismos filtros y hoja desde donde se abrió la ficha
  const boardSearch = (location.state as PlayerLocationState | null)?.boardSearch;
  const backLink = <Link to={`/pizarra${boardSearch ?? ''}`}>volver a la pizarra</Link>;

  function renderQuote() {
    if (quote.noQuote) {
      return <SheetNote variant="info" text="sin cotización: este jugador todavía no fue cotizado" />;
    }
    if (quote.status === 'error') {
      return (
        <SheetNote
          variant="error"
          text="no se pudo cargar la cotización"
          retry={{ label: 'reintentar la cotización', onClick: quote.retry }}
        />
      );
    }
    if (quote.status !== 'success' || !quote.quote) {
      return <SheetNote variant="loading" text="cargando la cotización..." />;
    }
    return <QuotePanel quote={quote.quote} />;
  }

  function renderHistory() {
    if (history.status === 'error') {
      return (
        <SheetNote
          variant="error"
          text="no se pudo cargar la evolución"
          retry={{ label: 'reintentar la evolución', onClick: history.retry }}
        />
      );
    }
    if (history.status !== 'success' || !history.quotes) {
      return <SheetNote variant="loading" text="cargando la evolución..." />;
    }
    return (
      <>
        <QuoteChart quotes={history.quotes} />
        <QuoteTable quotes={history.quotes} />
      </>
    );
  }

  function renderQuoteSlot() {
    return (
      <>
        <section className={styles.section} aria-labelledby={quoteHeadingId}>
          <h2 id={quoteHeadingId} className={styles.heading}>
            Cotización
          </h2>
          {renderQuote()}
        </section>
        {/* Un jugador sin ninguna cotización no tiene evolución que mostrar */}
        {quote.noQuote ? null : (
          <section className={styles.section} aria-labelledby={historyHeadingId}>
            <h2 id={historyHeadingId} className={styles.heading}>
              Evolución semanal
            </h2>
            <DateRangePicker value={range} onChange={setRange} />
            {renderHistory()}
          </section>
        )}
      </>
    );
  }

  function renderContent() {
    if (notFound) {
      return (
        <>
          <ChalkNote variant="info">jugador no encontrado</ChalkNote>
          <p className={styles.back}>{backLink}</p>
        </>
      );
    }
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading" />;
    if (status === 'error' || !player) {
      return (
        <>
          <ChalkNote variant="error" action={{ label: 'reintentar', onClick: retry }}>
            no se pudo conectar con la pizarra
          </ChalkNote>
          <p className={styles.back}>{backLink}</p>
        </>
      );
    }
    return <ScoutSheet player={player} backLink={backLink} quoteSlot={renderQuoteSlot()} />;
  }

  return (
    <Board>
      <div className={styles.content}>{renderContent()}</div>
    </Board>
  );
}
