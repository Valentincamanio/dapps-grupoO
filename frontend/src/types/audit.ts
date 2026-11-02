// Auditoría del mercado (GET /audit), copiada del contrato de la 008 (AuditPage, AuditEntry,
// AuditParty y PartyState). Los campos que no corresponden al tipo de hecho vienen en null.
import type { PlayerPage } from './api';
import type { OrderSide } from './market';

export const AUDIT_EVENT_TYPES = ['ORDER_CREATED', 'TRADE_EXECUTED', 'ORDER_CANCELLED'] as const;
export type AuditEventType = (typeof AUDIT_EVENT_TYPES)[number];

export type AuditActorType = 'USER' | 'SUPERUSER' | 'SYSTEM';
export type AuditPartyRole = 'OWNER' | 'BUYER' | 'SELLER';
export type CancelReason = 'USER_REQUEST' | 'INSUFFICIENT_CREDITS';

// Una foto de una parte: saldo libre, saldo reservado y tokens del jugador
export interface PartyState {
  availableBalance: number;
  reservedBalance: number;
  tokens: number;
}

export interface AuditParty {
  role: AuditPartyRole;
  userId: number;
  before: PartyState;
  after: PartyState;
}

export interface AuditEntry {
  id: number;
  // Instante ISO 8601
  occurredAt: string;
  eventType: AuditEventType;
  actorType: AuditActorType;
  // null cuando el autor es el sistema
  actorId: number | null;
  playerId: number;
  orderId: number | null;
  tradeId: number | null;
  side: OrderSide | null;
  quantity: number;
  tokenPrice: number | null;
  total: number | null;
  quoteId: number | null;
  cancelReason: CancelReason | null;
  detail: string;
  // El dueño en una orden creada o cancelada; el comprador y el vendedor en una operación
  parties: AuditParty[];
}

export interface AuditPage extends Omit<PlayerPage, 'content'> {
  // De la más nueva a la más vieja
  content: AuditEntry[];
}

// Filtros opcionales y combinables; las fechas son AAAA-MM-DD en hora de Argentina, incluidas
export interface AuditQuery {
  eventType?: AuditEventType;
  // Autor o cualquiera de las partes
  userId?: number;
  playerId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}
