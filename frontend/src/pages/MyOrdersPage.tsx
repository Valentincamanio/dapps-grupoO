// Las órdenes del usuario de la sesión (/ordenes): en cualquier estado, de la más nueva a la más vieja,
// con la hoja en la dirección. Una pendiente o parcial se cancela después de confirmar (US6); lo
// ejecutado se conserva. Después de cancelar se recarga la lista, y useOrderCommands pone al día el
// saldo de la barra.
import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { OrderList } from '../components/OrderList';
import { Pager } from '../components/Pager';
import { useMyOrders } from '../hooks/useMyOrders';
import { useOrderCommands } from '../hooks/useOrderCommands';
import type { UserOrder } from '../types/market';
import { formatPendingTokens, formatTokens, sideLabel } from '../utils/market';
import { formatCredits } from '../utils/quotes';
import styles from './MyOrdersPage.module.css';

// Órdenes por hoja
const SHEET_SIZE = 10;

// La hoja visible cuenta desde 1; un valor que no es una hoja no cuenta
function parseSheet(params: URLSearchParams): number {
  const sheet = Number(params.get('page'));
  return Number.isInteger(sheet) && sheet >= 1 ? sheet : 1;
}

// Lo que se libera al cancelar: la reserva de una compra o los tokens comprometidos de una venta
function releasedText(order: UserOrder): string {
  return order.side === 'BUY'
    ? `Se liberan ${formatCredits(order.reservedCredits)} reservados.`
    : order.pendingQuantity === 1
      ? 'Se libera 1 token comprometido.'
      : `Se liberan ${formatTokens(order.pendingQuantity)} comprometidos.`;
}

export function MyOrdersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const sheet = parseSheet(searchParams);
  const { status, page, orders, reload } = useMyOrders({ page: sheet - 1, size: SHEET_SIZE });
  const { cancellation, cancel } = useOrderCommands();
  // La orden que se está por cancelar, mientras se confirma
  const [confirming, setConfirming] = useState<UserOrder | null>(null);
  const busy = cancellation.status === 'running';

  function goToSheet(next: number) {
    const params = new URLSearchParams();
    if (next > 1) params.set('page', String(next));
    setSearchParams(params, { replace: false });
  }

  function ask(order: UserOrder) {
    cancellation.reset();
    setConfirming(order);
  }

  async function confirm() {
    if (!confirming) return;
    await cancel(confirming.id);
    setConfirming(null);
    // Salga bien o mal, la lista se vuelve a pedir: otro pedido pudo cambiar la orden
    reload();
  }

  function renderOutcome() {
    if (cancellation.status === 'error' && cancellation.error) {
      return <ChalkNote variant="error">{cancellation.error.message}</ChalkNote>;
    }
    if (cancellation.status === 'success' && cancellation.data) {
      const { order, availableBalance } = cancellation.data;
      return (
        <ChalkNote variant="success">
          {/* El punto de "cr." cierra la oración */}
          Cancelaste la {sideLabel(order.side)} de {order.player.name}:{' '}
          {order.cancelledQuantity === 1 ? 'se canceló' : 'se cancelaron'} {formatTokens(order.cancelledQuantity)}.
          Saldo libre: {formatCredits(availableBalance)}
        </ChalkNote>
      );
    }
    return null;
  }

  function renderContent() {
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading" />;
    if (status === 'error' || !page) {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: reload }}>
          no se pudieron cargar tus órdenes
        </ChalkNote>
      );
    }
    if (page.totalElements === 0) return <ChalkNote variant="info">todavía no tenés órdenes</ChalkNote>;
    if (orders.length === 0) {
      return (
        <ChalkNote variant="info" action={{ label: 'volver a la primera hoja', onClick: () => goToSheet(1) }}>
          esta hoja de órdenes está vacía
        </ChalkNote>
      );
    }

    return (
      <>
        <OrderList
          variant="own"
          orders={orders}
          onCancel={ask}
          cancellingId={busy ? confirming?.id : undefined}
        />
        <Pager
          sheet={page.page + 1}
          totalSheets={page.totalPages}
          hasPrevious={page.hasPrevious}
          hasNext={page.hasNext}
          onPrevious={() => goToSheet(page.page)}
          onNext={() => goToSheet(page.page + 2)}
        />
      </>
    );
  }

  return (
    <section className={styles.orders}>
      <ChalkText as="h1" variant="title">
        Mis órdenes
      </ChalkText>
      {renderOutcome()}
      {renderContent()}
      {confirming ? (
        <ConfirmDialog
          title={`¿Cancelar la ${sideLabel(confirming.side)} de ${confirming.player.name}?`}
          confirmLabel="Confirmar cancelación"
          cancelLabel="Volver"
          busy={busy}
          onConfirm={() => void confirm()}
          onCancel={() => setConfirming(null)}
        >
          <p className={styles.detail}>
            {confirming.pendingQuantity === 1 ? 'Se cancela' : 'Se cancelan'}{' '}
            {formatPendingTokens(confirming.pendingQuantity)}; lo ejecutado (
            {formatTokens(confirming.executedQuantity)}) se conserva. {releasedText(confirming)}
          </p>
        </ConfirmDialog>
      ) : null}
    </section>
  );
}
