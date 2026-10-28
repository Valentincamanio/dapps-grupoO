// Comprar, vender y cancelar sobre useMutation (research D27). Después de cada operación que sale
// bien vuelve a pedir el perfil, así la barra muestra el saldo libre nuevo (FR-062). Un rechazo del
// backend queda en el error, con su mensaje en español, y no se da la operación por hecha.
import { useCallback } from 'react';
import { buyTokens, cancelOrder, sellTokens } from '../services/marketService';
import type { ApiError } from '../types/api';
import type { OrderCancellation, OrderExecution, OrderRequest, OrderSide } from '../types/market';
import { useMutation, type MutationStatus } from './useMutation';
import { useSession } from './useSession';

interface CommandState<T> {
  status: MutationStatus;
  // El resultado de la última operación que salió bien
  data: T | undefined;
  error: ApiError | undefined;
  reset: () => void;
}

interface UseOrderCommandsResult {
  // Comprar y vender comparten el estado: la ficha muestra el resultado de la última operación
  trade: CommandState<OrderExecution>;
  cancellation: CommandState<OrderCancellation>;
  // Devuelven el resultado, o undefined si el backend rechazó el pedido
  buy: (order: OrderRequest) => Promise<OrderExecution | undefined>;
  sell: (order: OrderRequest) => Promise<OrderExecution | undefined>;
  cancel: (orderId: number) => Promise<OrderCancellation | undefined>;
}

interface TradeCommand {
  side: OrderSide;
  order: OrderRequest;
}

function placeOrder({ side, order }: TradeCommand): Promise<OrderExecution> {
  return side === 'BUY' ? buyTokens(order) : sellTokens(order);
}

export function useOrderCommands(): UseOrderCommandsResult {
  const { refreshProfile } = useSession();
  const { run: runTrade, ...trade } = useMutation<TradeCommand, OrderExecution>(placeOrder);
  const { run: runCancel, ...cancellation } = useMutation<number, OrderCancellation>(cancelOrder);

  // La operación ya se hizo: si el perfil no se puede pedir, la barra se pone al día en el
  // próximo pedido, y una sesión vencida la cierra el cliente HTTP
  const refreshAfter = useCallback(
    async <T>(result: T | undefined): Promise<T | undefined> => {
      if (result !== undefined) await refreshProfile().catch(() => undefined);
      return result;
    },
    [refreshProfile],
  );

  const buy = useCallback(
    async (order: OrderRequest) => refreshAfter(await runTrade({ side: 'BUY', order })),
    [refreshAfter, runTrade],
  );

  const sell = useCallback(
    async (order: OrderRequest) => refreshAfter(await runTrade({ side: 'SELL', order })),
    [refreshAfter, runTrade],
  );

  const cancel = useCallback(
    async (orderId: number) => refreshAfter(await runCancel(orderId)),
    [refreshAfter, runCancel],
  );

  return { trade, cancellation, buy, sell, cancel };
}
