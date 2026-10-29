// Ficha de un jugador: hoja de scout dentro del pizarrón, con su cotización y su evolución. Debajo
// de la hoja, sobre el pizarrón, sus órdenes pendientes (para todos) y el panel para operar (solo un
// usuario común, FR-065).
import { useId, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { Board } from '../components/Board';
import { ChalkNote } from '../components/ChalkNote';
import { DateRangePicker } from '../components/DateRangePicker';
import { OrderList } from '../components/OrderList';
import { QuoteChart } from '../components/QuoteChart';
import { QuotePanel } from '../components/QuotePanel';
import { QuoteTable } from '../components/QuoteTable';
import { ScoutSheet } from '../components/ScoutSheet';
import { TradePanel } from '../components/TradePanel';
import { useMarketOrders } from '../hooks/useMarketOrders';
import { useOrderCommands } from '../hooks/useOrderCommands';
import { usePlayer } from '../hooks/usePlayer';
import { usePlayerQuote } from '../hooks/usePlayerQuote';
import { useQuoteHistory } from '../hooks/useQuoteHistory';
import { useSession } from '../hooks/useSession';
import type { Player } from '../types/api';
import type { OrderSide } from '../types/market';
import type { QuoteRange } from '../types/quotes';
import styles from './PlayerPage.module.css';

// Las pendientes de un jugador entran en una sola hoja: la orden inicial, la más vieja, va primero
const PLAYER_ORDERS_SIZE = 50;

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
    return (
      <>
        <ScoutSheet player={player} backLink={backLink} quoteSlot={renderQuoteSlot()} />
        <PlayerMarket
          player={player}
          quoteReady={quote.status === 'success'}
          tokenPrice={quote.quote?.tokenPrice ?? null}
        />
      </>
    );
  }

  return (
    <Board>
      <div className={styles.content}>{renderContent()}</div>
    </Board>
  );
}

interface PlayerMarketProps {
  player: Player;
  // La cotización ya se sabe: la hay o el jugador no tiene (mientras carga o si falló, no se opera)
  quoteReady: boolean;
  tokenPrice: number | null;
}

// Las órdenes pendientes del jugador, incluida la inicial con los tokens que le quedan (FR-064), y el
// panel para operar. Se monta con el jugador ya cargado, así las órdenes se piden una sola vez
function PlayerMarket({ player, quoteReady, tokenPrice }: PlayerMarketProps) {
  const { profile } = useSession();
  const orders = useMarketOrders({ playerId: player.id, size: PLAYER_ORDERS_SIZE });
  const { trade, buy, sell } = useOrderCommands();
  const ordersHeadingId = useId();
  const tradeHeadingId = useId();
  const canTrade = profile?.role === 'USER';
  const initialPending = orders.orders.find((order) => order.ownerType === 'ADMIN')?.pendingQuantity ?? 0;

  // Después de una operación que salió bien, las pendientes cambiaron
  async function place(side: OrderSide, quantity: number) {
    const order = { playerId: player.id, quantity };
    const result = await (side === 'BUY' ? buy(order) : sell(order));
    if (result) orders.reload();
  }

  function renderOrders() {
    if (orders.status === 'error') {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar las órdenes', onClick: orders.reload }}>
          no se pudieron cargar las órdenes pendientes
        </ChalkNote>
      );
    }
    if (orders.status !== 'success') return <ChalkNote variant="loading">cargando las órdenes...</ChalkNote>;
    return (
      <OrderList
        variant="market"
        layout="list"
        orders={orders.orders}
        caption={`Órdenes con tokens pendientes de ${player.name}, de la más vieja a la más nueva`}
        emptyMessage="no hay órdenes pendientes de este jugador."
      />
    );
  }

  function renderTrade() {
    if (orders.status === 'error') {
      return <ChalkNote variant="info">para operar hacen falta las órdenes pendientes</ChalkNote>;
    }
    if (!quoteReady || orders.status !== 'success') {
      return <ChalkNote variant="loading">preparando el panel...</ChalkNote>;
    }
    return (
      <TradePanel
        playerName={player.name}
        active={player.active !== false}
        tokenPrice={tokenPrice}
        initialPending={initialPending}
        busy={trade.status === 'running'}
        result={trade.status === 'success' ? trade.data : undefined}
        error={trade.status === 'error' ? trade.error : undefined}
        onBuy={(quantity) => place('BUY', quantity)}
        onSell={(quantity) => place('SELL', quantity)}
      />
    );
  }

  return (
    <>
      {canTrade ? (
        <section className={styles.market} aria-labelledby={tradeHeadingId}>
          <h2 id={tradeHeadingId} className={styles.marketHeading}>
            Operar
          </h2>
          {renderTrade()}
        </section>
      ) : null}
      <section className={styles.market} aria-labelledby={ordersHeadingId}>
        <h2 id={ordersHeadingId} className={styles.marketHeading}>
          Órdenes pendientes
        </h2>
        {renderOrders()}
      </section>
    </>
  );
}
