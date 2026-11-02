// Textos de la auditoría del mercado.
import type { AuditActorType, AuditEventType, AuditPartyRole, CancelReason } from '../types/audit';
import { formatBalance } from './format';

const EVENT_TYPES: Record<AuditEventType, string> = {
  ORDER_CREATED: 'orden creada',
  TRADE_EXECUTED: 'operación',
  ORDER_CANCELLED: 'cancelación',
};

// En plural, para los filtros
const EVENT_TYPE_FILTERS: Record<AuditEventType, string> = {
  ORDER_CREATED: 'órdenes creadas',
  TRADE_EXECUTED: 'operaciones',
  ORDER_CANCELLED: 'cancelaciones',
};

const ROLES: Record<AuditPartyRole, string> = {
  OWNER: 'dueño',
  BUYER: 'comprador',
  SELLER: 'vendedor',
};

const CANCEL_REASONS: Record<CancelReason, string> = {
  USER_REQUEST: 'la canceló el usuario',
  INSUFFICIENT_CREDITS: 'sin créditos suficientes',
};

export function eventTypeLabel(type: AuditEventType): string {
  return EVENT_TYPES[type];
}

export function eventTypeFilterLabel(type: AuditEventType): string {
  return EVENT_TYPE_FILTERS[type];
}

export function partyRoleLabel(role: AuditPartyRole): string {
  return ROLES[role];
}

export function cancelReasonLabel(reason: CancelReason): string {
  return CANCEL_REASONS[reason];
}

// Quién hizo el hecho: el superusuario, el sistema o un usuario por su id
export function actorLabel(actorType: AuditActorType, actorId: number | null): string {
  if (actorType === 'SUPERUSER') return 'superusuario';
  if (actorType === 'SYSTEM' || actorId === null) return 'sistema';
  return `usuario #${actorId}`;
}

// "1.000,00 → 990,00", o solo el valor si no cambió
export function balanceChange(before: number, after: number): string {
  return before === after ? formatBalance(before) : `${formatBalance(before)} → ${formatBalance(after)}`;
}

export function tokensChange(before: number, after: number): string {
  return before === after ? String(before) : `${before} → ${after}`;
}
