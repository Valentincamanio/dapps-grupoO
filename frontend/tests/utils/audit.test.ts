import { describe, expect, it } from 'vitest';
import {
  actorLabel,
  balanceChange,
  cancelReasonLabel,
  eventTypeFilterLabel,
  eventTypeLabel,
  partyRoleLabel,
  tokensChange,
} from '../../src/utils/audit';

describe('actorLabel', () => {
  it('nombra al usuario por su id, al superusuario y al sistema', () => {
    const labels = [actorLabel('USER', 7), actorLabel('SUPERUSER', 1), actorLabel('SYSTEM', null)];

    expect(labels).toEqual(['usuario #7', 'superusuario', 'sistema']);
  });

  it('un usuario sin id se toma como el sistema', () => {
    const label = actorLabel('USER', null);

    expect(label).toBe('sistema');
  });
});

describe('cambios de una parte', () => {
  it('muestra el antes y el después cuando cambia, y solo el valor si no', () => {
    const changed = balanceChange(1000, 990.5);
    const same = balanceChange(10, 10);
    const tokens = tokensChange(0, 10);
    const sameTokens = tokensChange(100, 100);

    expect(changed).toBe('1.000,00 → 990,50');
    expect(same).toBe('10,00');
    expect(tokens).toBe('0 → 10');
    expect(sameTokens).toBe('100');
  });
});

describe('etiquetas', () => {
  it('traduce los tipos de hecho, los papeles y los motivos de cancelación', () => {
    const labels = [
      eventTypeLabel('ORDER_CREATED'),
      eventTypeLabel('TRADE_EXECUTED'),
      eventTypeLabel('ORDER_CANCELLED'),
      eventTypeFilterLabel('TRADE_EXECUTED'),
      partyRoleLabel('OWNER'),
      partyRoleLabel('BUYER'),
      partyRoleLabel('SELLER'),
      cancelReasonLabel('INSUFFICIENT_CREDITS'),
    ];

    expect(labels).toEqual([
      'orden creada',
      'operación',
      'cancelación',
      'operaciones',
      'dueño',
      'comprador',
      'vendedor',
      'sin créditos suficientes',
    ]);
  });
});
