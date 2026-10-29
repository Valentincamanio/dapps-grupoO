// Historial de operaciones del usuario de la sesión (/operaciones, FR-043 y FR-044): de la más nueva a
// la más vieja, con los filtros player, side, from y to y la hoja en la dirección. La contraparte se
// dice por su tipo, nunca quién es, y la ganancia realizada va solo en las ventas.
import { useCallback, useEffect, useId, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { DateRangePicker } from '../components/DateRangePicker';
import { Pager } from '../components/Pager';
import { ProfitBadge } from '../components/ProfitBadge';
import { TapeTab } from '../components/TapeTab';
import { useCatalog } from '../hooks/useCatalog';
import { useTransactions } from '../hooks/useTransactions';
import {
  ORDER_SIDES,
  type OrderSide,
  type Transaction,
  type TransactionFilters,
  type TransactionQuery,
} from '../types/market';
import type { QuoteRange } from '../types/quotes';
import { counterpartyLabel, formatScore, sideLabel } from '../utils/market';
import { formatCredits, formatInstant, formatTokenPrice } from '../utils/quotes';
import styles from './TransactionsPage.module.css';

// Operaciones por hoja del historial
const SHEET_SIZE = 10;

const DAY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

const SIDE_TABS: Record<OrderSide, string> = {
  BUY: 'compras',
  SELL: 'ventas',
};

// Un valor que no es del backend no filtra; la hoja visible cuenta desde 1
function parseFilters(params: URLSearchParams): TransactionFilters {
  const player = Number(params.get('player'));
  const side = params.get('side');
  const from = params.get('from');
  const to = params.get('to');
  const page = Number(params.get('page'));

  return {
    player: Number.isInteger(player) && player >= 1 ? player : null,
    side: ORDER_SIDES.includes(side as OrderSide) ? (side as OrderSide) : null,
    from: from && DAY_PATTERN.test(from) ? from : null,
    to: to && DAY_PATTERN.test(to) ? to : null,
    page: Number.isInteger(page) && page >= 1 ? page : 1,
  };
}

// Los filtros vacíos y la primera hoja no aparecen
function toSearchParams(filters: TransactionFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.player) params.set('player', String(filters.player));
  if (filters.side) params.set('side', filters.side);
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  if (filters.page > 1) params.set('page', String(filters.page));
  return params;
}

// Hacia el backend la hoja cuenta desde 0
function toQuery(filters: TransactionFilters): TransactionQuery {
  return {
    playerId: filters.player ?? undefined,
    side: filters.side ?? undefined,
    from: filters.from ?? undefined,
    to: filters.to ?? undefined,
    page: filters.page - 1,
    size: SHEET_SIZE,
  };
}

function hasFilters(filters: TransactionFilters): boolean {
  return filters.player !== null || filters.side !== null || filters.from !== null || filters.to !== null;
}

export function TransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => parseFilters(searchParams), [searchParams]);
  const { status, page, transactions, error, retry } = useTransactions(toQuery(filters));
  const catalog = useCatalog();
  const { ensureLoaded } = catalog;
  const playerSelectId = useId();
  // Cambia al limpiar, para que el selector de fechas olvide lo escrito
  const [rangeKey, setRangeKey] = useState(0);

  // El selector de jugador ofrece todo el catálogo
  useEffect(() => {
    void ensureLoaded();
  }, [ensureLoaded]);

  const write = useCallback(
    (next: TransactionFilters) => setSearchParams(toSearchParams(next), { replace: false }),
    [setSearchParams],
  );

  // Cambiar cualquier filtro vuelve a la primera hoja
  function change(changed: Partial<TransactionFilters>) {
    write({ ...filters, ...changed, page: 1 });
  }

  function changeRange(range: QuoteRange) {
    change({ from: range.from ?? null, to: range.to ?? null });
  }

  function clear() {
    setRangeKey((key) => key + 1);
    setSearchParams(new URLSearchParams(), { replace: false });
  }

  const players = useMemo(
    () => [...catalog.players].sort((a, b) => a.name.localeCompare(b.name, 'es')),
    [catalog.players],
  );

  function renderContent() {
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading" />;
    if (status === 'error' || !page) {
      // Un filtro rechazado por el servidor (por ejemplo, un rango invertido en la dirección) se dice
      // con su mensaje; cualquier otro error se puede reintentar
      if (error?.status === 400) {
        return (
          <ChalkNote variant="error" action={{ label: 'limpiar filtros', onClick: clear }}>
            {error.message}
          </ChalkNote>
        );
      }
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: retry }}>
          no se pudo cargar el historial
        </ChalkNote>
      );
    }
    if (page.totalElements === 0) {
      return hasFilters(filters) ? (
        <ChalkNote variant="info" action={{ label: 'limpiar filtros', onClick: clear }}>
          no hay operaciones con esos filtros
        </ChalkNote>
      ) : (
        <ChalkNote variant="info">todavía no tenés operaciones</ChalkNote>
      );
    }
    if (transactions.length === 0) {
      const firstSheet = () => write({ ...filters, page: 1 });
      return (
        <ChalkNote variant="info" action={{ label: 'volver a la primera hoja', onClick: firstSheet }}>
          esta hoja del historial está vacía
        </ChalkNote>
      );
    }

    return (
      <>
        <ul className={styles.list} aria-label="Tus operaciones, de la más nueva a la más vieja">
          {transactions.map((transaction) => (
            <TransactionCard key={transaction.id} transaction={transaction} />
          ))}
        </ul>
        <Pager
          sheet={page.page + 1}
          totalSheets={page.totalPages}
          hasPrevious={page.hasPrevious}
          hasNext={page.hasNext}
          onPrevious={() => write({ ...filters, page: page.page })}
          onNext={() => write({ ...filters, page: page.page + 2 })}
        />
      </>
    );
  }

  return (
    <section className={styles.transactions}>
      <ChalkText as="h1" variant="title">
        Operaciones
      </ChalkText>
      <div className={styles.filters}>
        <div className={styles.group} role="group" aria-label="filtrar por compra o venta">
          <TapeTab label="todas" active={filters.side === null} onSelect={() => change({ side: null })} />
          {ORDER_SIDES.map((side) => (
            <TapeTab
              key={side}
              label={SIDE_TABS[side]}
              active={filters.side === side}
              onSelect={() => change({ side })}
            />
          ))}
        </div>
        <div className={styles.paper}>
          <div className={styles.field}>
            <label htmlFor={playerSelectId}>Jugador</label>
            <select
              id={playerSelectId}
              value={filters.player ?? ''}
              onChange={(event) => {
                const { value } = event.target;
                change({ player: value === '' ? null : Number(value) });
              }}
            >
              <option value="">todos</option>
              {/* El jugador de la dirección se conserva aunque el catálogo todavía no esté */}
              {filters.player !== null && !players.some((player) => player.id === filters.player) ? (
                <option value={filters.player}>jugador {filters.player}</option>
              ) : null}
              {players.map((player) => (
                <option key={player.id} value={player.id}>
                  {player.name}
                </option>
              ))}
            </select>
          </div>
          <DateRangePicker
            key={rangeKey}
            value={{ from: filters.from ?? undefined, to: filters.to ?? undefined }}
            onChange={changeRange}
          />
        </div>
        {hasFilters(filters) ? <ChalkButton onClick={clear}>limpiar filtros</ChalkButton> : null}
      </div>
      {renderContent()}
    </section>
  );
}

// Una operación como tarjeta: se lee igual a 360 px que en una pantalla ancha
function TransactionCard({ transaction }: { transaction: Transaction }) {
  const { quote } = transaction;
  const sale = transaction.side === 'SELL';

  return (
    <li className={styles.card}>
      <div className={styles.cardHeader}>
        <span className={styles.player}>{transaction.player.name}</span>
        <span className={styles.side}>{sideLabel(transaction.side)}</span>
      </div>
      <dl className={styles.fields}>
        <div>
          <dt>Fecha</dt>
          <dd>{formatInstant(transaction.executedAt)}</dd>
        </div>
        <div>
          <dt>Cantidad</dt>
          <dd className={styles.number}>{transaction.quantity}</dd>
        </div>
        <div>
          <dt>Precio</dt>
          <dd className={styles.number}>{formatTokenPrice(transaction.tokenPrice)}</dd>
        </div>
        <div>
          <dt>Total</dt>
          <dd className={styles.number}>{formatCredits(transaction.total)}</dd>
        </div>
        <div>
          <dt>Contraparte</dt>
          <dd>{counterpartyLabel(transaction.counterparty)}</dd>
        </div>
        <div>
          <dt>Orden</dt>
          <dd className={styles.number}>#{transaction.orderId}</dd>
        </div>
        <div>
          <dt>Cotización</dt>
          <dd>
            {formatCredits(quote.value)} desde {formatInstant(quote.validFrom)}, score {formatScore(quote.score)},{' '}
            {quote.strategy.name} v{quote.strategy.version}
          </dd>
        </div>
        {sale ? (
          <div>
            <dt>Ganancia realizada</dt>
            <dd>
              <ProfitBadge amount={transaction.realizedProfit} />
            </dd>
          </div>
        ) : null}
      </dl>
    </li>
  );
}
