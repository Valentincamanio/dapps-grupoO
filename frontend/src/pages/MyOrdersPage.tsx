// Las órdenes del usuario de la sesión (/ordenes): de la más nueva a la más vieja, con el filtro de
// estado y la hoja en la dirección. Al entrar se ven las abiertas (pendientes y parciales); las
// ejecutadas, las canceladas o todas, eligiendo el filtro. Una pendiente o parcial se cancela después
// de confirmar (US6); lo ejecutado se conserva. Después de cancelar se recarga la lista, y
// useOrderCommands pone al día el saldo de la barra.
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { OrderList } from '../components/OrderList';
import { Pager } from '../components/Pager';
import { SectionNav } from '../components/SectionNav';
import { TapeTab } from '../components/TapeTab';
import { useMyOrders } from '../hooks/useMyOrders';
import { useOrderCommands } from '../hooks/useOrderCommands';
import type { UserOrder } from '../types/market';
import {
  DEFAULT_ORDER_FILTER,
  formatPendingTokens,
  formatTokens,
  ORDER_FILTERS,
  parseOrderFilter,
  sideLabel,
  statusesOf,
  type OrderFilter,
} from '../utils/market';
import { formatCredits } from '../utils/quotes';
import { TEAM_SECTIONS } from '../utils/sections';
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
  const filter = parseOrderFilter(searchParams.get('estado'));
  const { status, page, orders, reload } = useMyOrders({
    page: sheet - 1,
    size: SHEET_SIZE,
    status: statusesOf(filter),
  });
  const { cancellation, cancel } = useOrderCommands();
  // La orden que se está por cancelar, mientras se confirma
  const [confirming, setConfirming] = useState<UserOrder | null>(null);
  const busy = cancellation.status === 'running';

  // El filtro de entrada no se escribe en la dirección
  function navigate(nextFilter: OrderFilter, nextSheet: number) {
    const params = new URLSearchParams();
    if (nextFilter !== DEFAULT_ORDER_FILTER) params.set('estado', nextFilter);
    if (nextSheet > 1) params.set('page', String(nextSheet));
    setSearchParams(params, { replace: false });
  }

  function goToSheet(next: number) {
    navigate(filter, next);
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
    if (page.totalElements === 0) {
      if (filter === 'todas') return <ChalkNote variant="info">todavía no tenés órdenes</ChalkNote>;
      return (
        <ChalkNote variant="info" action={{ label: 'ver todas', onClick: () => navigate('todas', 1) }}>
          no tenés órdenes {filter}
        </ChalkNote>
      );
    }
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
          playerHref={(playerId) => `/jugadores/${playerId}`}
          LinkComponent={Link}
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
      <ChalkText as="h1" variant="srOnly">
        Mis órdenes
      </ChalkText>
      <SectionNav
        title="Mi equipo"
        label="secciones de mi equipo"
        sections={TEAM_SECTIONS}
        current="/ordenes"
        LinkComponent={Link}
      />
      <div className={styles.filters} role="group" aria-label="filtrar por estado">
        {ORDER_FILTERS.map((option) => (
          <TapeTab key={option} label={option} active={filter === option} onSelect={() => navigate(option, 1)} />
        ))}
      </div>
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
