// Auditoría simulada (GET /audit): una orden creada, una operación y una cancelación sobre los
// jugadores del catálogo, de la más nueva a la más vieja. Filtra y pagina como el backend; sin
// credencial, 401. La regla de que solo el ADMIN la ve vive en el backend.
import { http, HttpResponse } from 'msw';
import type { ApiError } from '../../src/types/api';
import { AUDIT_EVENT_TYPES, type AuditEntry, type AuditEventType, type AuditPage } from '../../src/types/audit';
import { PLAYERS } from './players';

const MAX_SIZE = 50;
const DAY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export const BUYER_ID = 7;
export const SELLER_ID = 9;

const state = (availableBalance: number, reservedBalance: number, tokens: number) => ({
  availableBalance,
  reservedBalance,
  tokens,
});

// Del más nuevo al más viejo
export const AUDIT_ENTRIES: AuditEntry[] = [
  {
    id: 3,
    occurredAt: '2026-10-30T15:00:00Z',
    eventType: 'ORDER_CANCELLED',
    actorType: 'USER',
    actorId: BUYER_ID,
    playerId: PLAYERS[1].id,
    orderId: 21,
    tradeId: null,
    side: 'BUY',
    quantity: 5,
    tokenPrice: null,
    total: null,
    quoteId: null,
    cancelReason: 'USER_REQUEST',
    detail: 'Se cancelan 5 tokens pendientes.',
    parties: [{ role: 'OWNER', userId: BUYER_ID, before: state(900, 50, 0), after: state(950, 0, 0) }],
  },
  {
    id: 2,
    occurredAt: '2026-10-20T12:00:00Z',
    eventType: 'TRADE_EXECUTED',
    actorType: 'USER',
    actorId: BUYER_ID,
    playerId: PLAYERS[0].id,
    orderId: 20,
    tradeId: 11,
    side: 'BUY',
    quantity: 10,
    tokenPrice: 1,
    total: 10,
    quoteId: 4,
    cancelReason: null,
    detail: '',
    parties: [
      { role: 'BUYER', userId: BUYER_ID, before: state(1000, 10, 0), after: state(990, 0, 10) },
      { role: 'SELLER', userId: SELLER_ID, before: state(0, 0, 100), after: state(10, 0, 90) },
    ],
  },
  {
    id: 1,
    occurredAt: '2026-10-05T10:00:00Z',
    eventType: 'ORDER_CREATED',
    actorType: 'SUPERUSER',
    actorId: SELLER_ID,
    playerId: PLAYERS[0].id,
    orderId: 1,
    tradeId: null,
    side: 'SELL',
    quantity: 100,
    tokenPrice: 1,
    total: 100,
    quoteId: null,
    cancelReason: null,
    detail: 'Orden inicial.',
    parties: [{ role: 'OWNER', userId: SELLER_ID, before: state(0, 0, 100), after: state(0, 0, 100) }],
  },
];

function apiError(status: number, error: string, message: string): Response {
  const body: ApiError = { timestamp: new Date().toISOString(), status, error, message, path: '/audit' };
  return HttpResponse.json(body, { status });
}

// El día del instante en hora de Argentina (UTC−3, sin horario de verano)
function argentinaDay(instant: string): string {
  return new Date(Date.parse(instant) - 3 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

export const auditHandlers = [
  http.get('*/api/audit', ({ request }) => {
    if (!request.headers.get('Authorization')) return apiError(401, 'Unauthorized', 'Se requiere una credencial.');
    const search = new URL(request.url).searchParams;
    const eventType = search.get('eventType');
    const userId = search.get('userId');
    const playerId = search.get('playerId');
    const from = search.get('from');
    const to = search.get('to');
    const page = Number(search.get('page') ?? 0);
    const size = Number(search.get('size') ?? 10);
    const invalid =
      (eventType !== null && !AUDIT_EVENT_TYPES.includes(eventType as AuditEventType)) ||
      (from !== null && !DAY_PATTERN.test(from)) ||
      (to !== null && !DAY_PATTERN.test(to)) ||
      !(page >= 0) ||
      !(size >= 1 && size <= MAX_SIZE);
    if (invalid) return apiError(400, 'Bad Request', 'Los parámetros enviados no son válidos.');

    const rows = AUDIT_ENTRIES.filter(
      (entry) =>
        (eventType === null || entry.eventType === eventType) &&
        (playerId === null || entry.playerId === Number(playerId)) &&
        (userId === null ||
          entry.actorId === Number(userId) ||
          entry.parties.some((party) => party.userId === Number(userId))) &&
        (from === null || argentinaDay(entry.occurredAt) >= from) &&
        (to === null || argentinaDay(entry.occurredAt) <= to),
    );
    const totalPages = Math.ceil(rows.length / size);
    const body: AuditPage = {
      content: rows.slice(page * size, page * size + size),
      page,
      size,
      totalElements: rows.length,
      totalPages,
      hasPrevious: page > 0,
      hasNext: page + 1 < totalPages,
    };
    return HttpResponse.json(body);
  }),
];
