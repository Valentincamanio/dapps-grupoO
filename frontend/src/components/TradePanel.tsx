// Panel para comprar y vender tokens desde la ficha (FR-063 y FR-064). Nada se envía sin pasar por
// la confirmación, que muestra la cantidad, el precio por token y el total estimado. Después muestra
// el resultado real que devolvió el backend, o el motivo del rechazo sin dar la operación por hecha.
// Recibe los datos y los callbacks: no pide nada a la API ni usa el router.
import { useState } from 'react';
import type { ApiError } from '../types/api';
import type { OrderExecution, OrderSide, TradeEstimate } from '../types/market';
import {
  counterpartyLabel,
  estimateBuy,
  estimateSell,
  formatPendingTokens,
  formatTokens,
  INITIAL_TOKEN_PRICE,
  sideLabel,
  tradeBlockOf,
} from '../utils/market';
import { formatCredits, formatTokenPrice } from '../utils/quotes';
import { ChalkButton } from './ChalkButton';
import { ChalkInput } from './ChalkInput';
import { ConfirmDialog } from './ConfirmDialog';
import styles from './TradePanel.module.css';

const MIN_QUANTITY = 1;
const MAX_QUANTITY = 100;
const QUANTITY_MESSAGE = 'La cantidad tiene que ser un entero entre 1 y 100.';

interface TradePanelProps {
  playerName: string;
  active: boolean;
  // Precio por token de la cotización vigente; null si el jugador no tiene cotización
  tokenPrice: number | null;
  // Tokens que le quedan a la orden inicial del superusuario; 0 si está agotada
  initialPending: number;
  // Mientras se espera la respuesta de una operación
  busy: boolean;
  // El resultado de la última operación que salió bien, si es la última que se pidió
  result: OrderExecution | undefined;
  // El rechazo de la última operación, si es la última que se pidió
  error: ApiError | undefined;
  // Terminan cuando el backend responde, salga bien o mal
  onBuy: (quantity: number) => Promise<unknown>;
  onSell: (quantity: number) => Promise<unknown>;
}

// La cantidad escrita, si es un entero de 1 a 100
function parseQuantity(raw: string): number | null {
  if (!/^\d+$/.test(raw.trim())) return null;
  const quantity = Number(raw);
  return quantity >= MIN_QUANTITY && quantity <= MAX_QUANTITY ? quantity : null;
}

export function TradePanel({
  playerName,
  active,
  tokenPrice,
  initialPending,
  busy,
  result,
  error,
  onBuy,
  onSell,
}: TradePanelProps) {
  const [rawQuantity, setRawQuantity] = useState('1');
  // El lado que se está confirmando
  const [confirming, setConfirming] = useState<OrderSide | null>(null);
  const quantity = parseQuantity(rawQuantity);
  const block = tradeBlockOf(active, tokenPrice !== null, initialPending);
  const notTradable = block?.kind === 'not-tradable';
  const saleBlocked = block?.kind === 'sale-blocked';

  async function confirm() {
    if (confirming === null || quantity === null) return;
    await (confirming === 'BUY' ? onBuy(quantity) : onSell(quantity));
    setConfirming(null);
  }

  return (
    <div className={styles.panel}>
      {block?.kind === 'not-tradable' ? (
        <p className={styles.notice}>No se puede operar: {block.reason}.</p>
      ) : null}
      {block?.kind === 'sale-blocked' ? (
        <p className={styles.notice}>
          Todavía no se puede vender: al administrador le {block.remainingTokens === 1 ? 'queda' : 'quedan'}{' '}
          {formatTokens(block.remainingTokens)} de este jugador. Mientras tanto se compra a{' '}
          {formatTokenPrice(INITIAL_TOKEN_PRICE)} por token.
        </p>
      ) : null}
      <div className={styles.controls}>
        <ChalkInput
          label="Cantidad de tokens"
          type="number"
          inputMode="numeric"
          min={MIN_QUANTITY}
          max={MAX_QUANTITY}
          step={1}
          value={rawQuantity}
          disabled={notTradable}
          error={quantity === null && !notTradable ? QUANTITY_MESSAGE : undefined}
          onChange={(event) => setRawQuantity(event.target.value)}
        />
        <div className={styles.actions}>
          <ChalkButton disabled={notTradable || quantity === null || busy} onClick={() => setConfirming('BUY')}>
            Comprar
          </ChalkButton>
          <ChalkButton
            disabled={notTradable || saleBlocked || quantity === null || busy}
            onClick={() => setConfirming('SELL')}
          >
            Vender
          </ChalkButton>
        </div>
      </div>
      {error && !busy ? (
        <p className={styles.error} role="alert">
          No se hizo la operación. {error.message}
        </p>
      ) : null}
      {result && !busy ? <TradeResult execution={result} /> : null}
      {confirming !== null && quantity !== null && tokenPrice !== null ? (
        <ConfirmDialog
          title={`¿${confirming === 'BUY' ? 'Comprar' : 'Vender'} ${formatTokens(quantity)}?`}
          confirmLabel={`Confirmar ${sideLabel(confirming)}`}
          busy={busy}
          onConfirm={() => void confirm()}
          onCancel={() => setConfirming(null)}
        >
          <TradeSummary
            playerName={playerName}
            quantity={quantity}
            tokenPrice={tokenPrice}
            estimate={
              confirming === 'BUY'
                ? estimateBuy(quantity, initialPending, tokenPrice)
                : estimateSell(quantity, tokenPrice)
            }
          />
        </ConfirmDialog>
      ) : null}
    </div>
  );
}

interface TradeSummaryProps {
  playerName: string;
  quantity: number;
  tokenPrice: number;
  estimate: TradeEstimate;
}

// Lo que se confirma: una compra toma primero los tokens del administrador a 1,0000
function TradeSummary({ playerName, quantity, tokenPrice, estimate }: TradeSummaryProps) {
  const { atInitialPrice, atQuotePrice } = estimate;
  let price = formatTokenPrice(tokenPrice);
  if (atInitialPrice > 0 && atQuotePrice > 0) {
    price = `${atInitialPrice} a ${formatTokenPrice(INITIAL_TOKEN_PRICE)} (del administrador) y ${atQuotePrice} a ${formatTokenPrice(tokenPrice)}`;
  } else if (atInitialPrice > 0) {
    price = `${formatTokenPrice(INITIAL_TOKEN_PRICE)} (del administrador)`;
  }
  return (
    <>
      <dl className={styles.summary}>
        <div>
          <dt>Jugador</dt>
          <dd>{playerName}</dd>
        </div>
        <div>
          <dt>Cantidad</dt>
          <dd>{formatTokens(quantity)}</dd>
        </div>
        <div>
          <dt>Precio por token</dt>
          <dd>{price}</dd>
        </div>
        <div>
          <dt>Total estimado</dt>
          <dd>{formatCredits(estimate.total)}</dd>
        </div>
      </dl>
      <p className={styles.hint}>Es un estimado: el precio final es el del momento de la ejecución.</p>
    </>
  );
}

// El resultado real: lo que se ejecutó en el acto, a qué precio, por cuánto y lo que quedó pendiente
function TradeResult({ execution }: { execution: OrderExecution }) {
  const { order, trades, executedQuantity, executedTotal } = execution;
  const verb = order.side === 'BUY' ? 'compraron' : 'vendieron';
  return (
    <div className={styles.result} role="status" aria-label="resultado de la operación">
      {executedQuantity > 0 ? (
        <p>
          {/* El punto de "cr." cierra la oración */}
          Se {verb} {formatTokens(executedQuantity)} por {formatCredits(executedTotal)}
        </p>
      ) : (
        <p>No se ejecutó nada en el acto.</p>
      )}
      {trades.length > 0 ? (
        <ul className={styles.trades}>
          {trades.map((trade) => (
            <li key={trade.id}>
              {formatTokens(trade.quantity)} a {formatTokenPrice(trade.tokenPrice)} por {formatCredits(trade.total)}
              , contraparte: {counterpartyLabel(trade.counterparty)}
            </li>
          ))}
        </ul>
      ) : null}
      {order.pendingQuantity > 0 ? (
        <p>
          {order.pendingQuantity === 1 ? 'Quedó' : 'Quedaron'} {formatPendingTokens(order.pendingQuantity)} en tu
          orden de {sideLabel(order.side)}.
        </p>
      ) : null}
    </div>
  );
}
