// Tipos de la API del mercado copiados del contrato
// specs/008-mercado-tokens-portfolio/contracts/market-api.yaml (data-model §10). Los enums van tal
// cual el backend; sus etiquetas en español están en utils/market.ts.
import type { League, Position } from './api';
import type { PageInfo, StrategyRef } from './quotes';

export const ORDER_SIDES = ['BUY', 'SELL'] as const;
export type OrderSide = (typeof ORDER_SIDES)[number];

export const ORDER_STATUSES = ['PENDING', 'PARTIAL', 'EXECUTED', 'CANCELLED'] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

// USER: usuario común. ADMIN: el superusuario, con sus órdenes iniciales
export const OWNER_TYPES = ['USER', 'ADMIN'] as const;
export type OwnerType = (typeof OWNER_TYPES)[number];

export interface PlayerRef {
  id: number;
  name: string;
  team: string;
  league: League;
}

// Una orden del listado público: nunca dice quién es el dueño, solo su tipo
export interface MarketOrder {
  id: number;
  player: PlayerRef;
  side: OrderSide;
  ownerType: OwnerType;
  status: OrderStatus;
  requestedQuantity: number;
  executedQuantity: number;
  pendingQuantity: number;
  // Instante ISO 8601 en UTC
  createdAt: string;
}

// Una orden propia: suma lo cancelado y la reserva (0 en las ventas)
export interface UserOrder extends MarketOrder {
  cancelledQuantity: number;
  reservedCredits: number;
}

// La cotización usada por una operación o una posición, con su score
export interface QuoteUsed {
  validFrom: string;
  // Valor del 100 % del jugador, dos decimales
  value: number;
  // value / 100, cuatro decimales
  tokenPrice: number;
  // De -1 a +1, seis decimales
  score: number;
  strategy: StrategyRef;
}

export interface TradeSummary {
  id: number;
  executedAt: string;
  quantity: number;
  // 1,0000 contra la orden inicial; si no, el de la cotización vigente
  tokenPrice: number;
  total: number;
  counterparty: OwnerType;
  quote: QuoteUsed;
}

// Saldo total, libre (total − reservado) y reservado para compras pendientes
export interface Balances {
  balance: number;
  availableBalance: number;
  reservedBalance: number;
}

export interface OrderRequest {
  playerId: number;
  // Entero de 1 a 100
  quantity: number;
}

// El resultado de comprar o vender: la orden, lo que se ejecutó en el acto y los saldos
export interface OrderExecution extends Balances {
  order: UserOrder;
  // En orden; puede estar vacía si todo quedó pendiente
  trades: TradeSummary[];
  executedQuantity: number;
  executedTotal: number;
}

export interface OrderCancellation extends Balances {
  order: UserOrder;
}

// PortfolioPosition y no Position, que ya es la posición del jugador en types/api.ts
export interface PortfolioPosition {
  player: PlayerRef & { position: Position; active: boolean };
  tokens: number;
  // Cada token es el 1 %
  sharePercent: number;
  // Tokens en ventas pendientes; solo en el portfolio actual
  committed?: number;
  // Nulos en el portfolio del superusuario, que no compró
  averagePrice: number | null;
  invested: number | null;
  quote: QuoteUsed;
  value: number;
  profit: number | null;
  // Nulo si no hay invertido
  profitPercent: number | null;
}

export interface PortfolioTotals {
  // Saldo total (a la fecha, si se pidió una)
  balance: number;
  // Solo en el portfolio actual
  availableBalance?: number;
  reservedBalance?: number;
  invested: number;
  value: number;
  profit: number;
  // Nula en el portfolio del superusuario
  realizedProfit: number | null;
  // Saldo total + valor de las posiciones
  equity: number;
}

export interface Portfolio {
  userId: number;
  // La fecha pedida, AAAA-MM-DD; null en el portfolio actual
  date: string | null;
  positions: PortfolioPosition[];
  totals: PortfolioTotals;
}

// Una operación del historial, vista por el usuario: su lado y su contraparte
export interface Transaction {
  id: number;
  executedAt: string;
  player: PlayerRef;
  side: OrderSide;
  quantity: number;
  tokenPrice: number;
  total: number;
  counterparty: OwnerType;
  // La orden propia a la que pertenece
  orderId: number;
  quote: QuoteUsed;
  // Solo en las ventas de un usuario
  realizedProfit?: number | null;
}

export interface MarketOrderPage extends PageInfo {
  content: MarketOrder[];
}

export interface UserOrderPage extends PageInfo {
  content: UserOrder[];
}

export interface TransactionPage extends PageInfo {
  content: Transaction[];
}

// Parámetros de GET /orders: page >= 0 y 1 <= size <= 50
export interface MarketOrderQuery {
  side?: OrderSide;
  league?: League;
  team?: string;
  ownerType?: OwnerType;
  playerId?: number;
  page?: number;
  size?: number;
}

// Parámetros de GET /users/{id}/transactions; las fechas, AAAA-MM-DD en hora de Argentina
export interface TransactionQuery {
  playerId?: number;
  side?: OrderSide;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

// Parámetros de GET /users/{id}/orders
export interface PageQuery {
  page?: number;
  size?: number;
}

// Tipos propios de la interfaz (data-model §10)

// Filtros de /mercado en la dirección; null = sin filtro
export interface MarketFilters {
  side: OrderSide | null;
  league: League | null;
  team: string | null;
  ownerType: OwnerType | null;
  // Hoja visible, desde 1, como en la pizarra
  page: number;
}

// Filtros de /operaciones en la dirección; null = sin filtro
export interface TransactionFilters {
  player: number | null;
  side: OrderSide | null;
  // AAAA-MM-DD, ambos extremos incluidos
  from: string | null;
  to: string | null;
  // Hoja visible, desde 1
  page: number;
}

// Lo que se estima antes de confirmar una compra o una venta; el precio final es el de la ejecución
export interface TradeEstimate {
  // Tokens al precio de la orden inicial (1,0000)
  atInitialPrice: number;
  // Tokens al precio por token de la cotización vigente
  atQuotePrice: number;
  // Total estimado en créditos, dos decimales
  total: number;
}

// Por qué no se puede operar desde la ficha, o null si se puede
export type TradeBlock =
  | { kind: 'not-tradable'; reason: string }
  | { kind: 'sale-blocked'; remainingTokens: number }
  | null;
