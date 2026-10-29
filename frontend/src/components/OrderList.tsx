// Lista de órdenes: el listado del mercado, las pendientes de la ficha y las órdenes propias. Nunca
// dice quién es el dueño, solo su tipo (FR-048). La variante "own" suma lo cancelado, la reserva y un
// botón para cancelar las abiertas. Recibe los datos y el callback: no pide nada a la API.
import type { MarketOrder, UserOrder } from '../types/market';
import { ownerTypeLabel, sideLabel, statusLabel } from '../utils/market';
import { formatCredits, formatInstant } from '../utils/quotes';
import { ChalkButton } from './ChalkButton';
import styles from './OrderList.module.css';

interface MarketVariantProps {
  variant: 'market';
  orders: MarketOrder[];
  caption?: string;
  emptyMessage?: string;
  // 'list' la muestra como una lista de tarjetas, para ponerla junto a otra tabla (la ficha ya tiene
  // la de cotizaciones). Por defecto, una tabla que pasa a tarjetas a 360 px
  layout?: 'table' | 'list';
}

interface OwnVariantProps {
  variant: 'own';
  orders: UserOrder[];
  caption?: string;
  emptyMessage?: string;
  // Sin callback no se ofrece cancelar
  onCancel?: (order: UserOrder) => void;
  // La orden que se está cancelando, para no pedirlo dos veces
  cancellingId?: number;
}

type OrderListProps = MarketVariantProps | OwnVariantProps;

const DEFAULT_CAPTIONS = {
  market: 'Órdenes con tokens pendientes, de la más vieja a la más nueva',
  own: 'Tus órdenes, de la más nueva a la más vieja',
};

const DEFAULT_EMPTY_MESSAGES = {
  market: 'no hay órdenes pendientes.',
  own: 'todavía no tenés órdenes.',
};

function isOpen(order: MarketOrder): boolean {
  return order.status === 'PENDING' || order.status === 'PARTIAL';
}

export function OrderList(props: OrderListProps) {
  const { variant, orders } = props;
  const own = props.variant === 'own' ? props : null;

  if (orders.length === 0) {
    return <p className={styles.empty}>{props.emptyMessage ?? DEFAULT_EMPTY_MESSAGES[variant]}</p>;
  }
  if (props.variant === 'market' && props.layout === 'list') {
    return <OrderCards orders={props.orders} caption={props.caption ?? DEFAULT_CAPTIONS.market} />;
  }

  return (
    <table className={styles.table}>
      <caption className={styles.caption}>{props.caption ?? DEFAULT_CAPTIONS[variant]}</caption>
      <thead>
        <tr>
          <th scope="col">Jugador</th>
          <th scope="col">Tipo</th>
          <th scope="col">Dueño</th>
          <th scope="col">Pedida</th>
          <th scope="col">Ejecutada</th>
          <th scope="col">Pendiente</th>
          {own ? <th scope="col">Cancelada</th> : null}
          {own ? <th scope="col">Reserva</th> : null}
          <th scope="col">Estado</th>
          <th scope="col">Fecha</th>
          {own?.onCancel ? <th scope="col">Acción</th> : null}
        </tr>
      </thead>
      <tbody>
        {orders.map((order) => (
          <tr key={order.id}>
            <th scope="row" data-label="Jugador" className={styles.player}>
              {order.player.name}
            </th>
            <td data-label="Tipo">{sideLabel(order.side)}</td>
            <td data-label="Dueño">{ownerTypeLabel(order.ownerType)}</td>
            <td data-label="Pedida" className={styles.number}>
              {order.requestedQuantity}
            </td>
            <td data-label="Ejecutada" className={styles.number}>
              {order.executedQuantity}
            </td>
            <td data-label="Pendiente" className={styles.number}>
              {order.pendingQuantity}
            </td>
            {own ? <OwnColumns order={order as UserOrder} /> : null}
            <td data-label="Estado" className={styles.status}>
              {statusLabel(order.status)}
            </td>
            <td data-label="Fecha">{formatInstant(order.createdAt)}</td>
            {own?.onCancel ? (
              <td data-label="Acción">
                {isOpen(order) ? (
                  <CancelButton
                    order={order as UserOrder}
                    busy={own.cancellingId === order.id}
                    onCancel={own.onCancel}
                  />
                ) : null}
              </td>
            ) : null}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// Las mismas órdenes del mercado como tarjetas con etiquetas, sin tabla
function OrderCards({ orders, caption }: { orders: MarketOrder[]; caption: string }) {
  return (
    <div>
      <p className={styles.caption}>{caption}</p>
      <ul className={styles.cards} aria-label={caption}>
        {orders.map((order) => (
          <li key={order.id} className={styles.card}>
            <span className={styles.player}>{order.player.name}</span>
            <dl className={styles.fields}>
              <div>
                <dt>Tipo</dt>
                <dd>{sideLabel(order.side)}</dd>
              </div>
              <div>
                <dt>Dueño</dt>
                <dd>{ownerTypeLabel(order.ownerType)}</dd>
              </div>
              <div>
                <dt>Pedida</dt>
                <dd className={styles.number}>{order.requestedQuantity}</dd>
              </div>
              <div>
                <dt>Ejecutada</dt>
                <dd className={styles.number}>{order.executedQuantity}</dd>
              </div>
              <div>
                <dt>Pendiente</dt>
                <dd className={styles.number}>{order.pendingQuantity}</dd>
              </div>
              <div>
                <dt>Estado</dt>
                <dd className={styles.status}>{statusLabel(order.status)}</dd>
              </div>
              <div>
                <dt>Fecha</dt>
                <dd>{formatInstant(order.createdAt)}</dd>
              </div>
            </dl>
          </li>
        ))}
      </ul>
    </div>
  );
}

function OwnColumns({ order }: { order: UserOrder }) {
  return (
    <>
      <td data-label="Cancelada" className={styles.number}>
        {order.cancelledQuantity}
      </td>
      <td data-label="Reserva" className={styles.number}>
        {formatCredits(order.reservedCredits)}
      </td>
    </>
  );
}

interface CancelButtonProps {
  order: UserOrder;
  busy: boolean;
  onCancel: (order: UserOrder) => void;
}

// El nombre accesible dice qué orden se cancela, porque hay un botón por fila
function CancelButton({ order, busy, onCancel }: CancelButtonProps) {
  return (
    <ChalkButton
      busy={busy}
      aria-label={`Cancelar la orden de ${sideLabel(order.side)} de ${order.player.name}`}
      onClick={() => onCancel(order)}
    >
      Cancelar
    </ChalkButton>
  );
}
