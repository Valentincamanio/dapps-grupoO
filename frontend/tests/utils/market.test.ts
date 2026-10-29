// Etiquetas, estimados y formatos del mercado, con los ejemplos del spec de la 008.
import { describe, expect, it } from 'vitest';
import {
  counterpartyLabel,
  estimateBuy,
  estimateSell,
  formatScore,
  formatSigned,
  formatSignedPercent,
  formatTokens,
  ownerTypeLabel,
  profitText,
  sideLabel,
  statusLabel,
  tradeBlockOf,
  tradeTotal,
} from '../../src/utils/market';
import { formatCredits } from '../../src/utils/quotes';

const CURRENCY_SYMBOLS = /[$€]|USD|EUR/;

describe('market', () => {
  it.each([
    ['BUY', 'compra'],
    ['SELL', 'venta'],
  ] as const)('el tipo %s se llama "%s"', (side, label) => {
    const text = sideLabel(side);

    expect(text).toBe(label);
  });

  it.each([
    ['PENDING', 'pendiente'],
    ['PARTIAL', 'parcial'],
    ['EXECUTED', 'ejecutada'],
    ['CANCELLED', 'cancelada'],
  ] as const)('el estado %s se llama "%s"', (status, label) => {
    const text = statusLabel(status);

    expect(text).toBe(label);
  });

  it('el tipo de usuario dueño es administrador o usuario', () => {
    const labels = [ownerTypeLabel('ADMIN'), ownerTypeLabel('USER')];

    expect(labels).toEqual(['administrador', 'usuario']);
  });

  it('la contraparte es el administrador u otro usuario, sin decir quién', () => {
    const labels = [counterpartyLabel('ADMIN'), counterpartyLabel('USER')];

    expect(labels).toEqual(['administrador', 'otro usuario']);
  });

  it.each([
    [3, 1.0333, 3.1],
    [10, 1.0345, 10.35],
    [10, 1, 10],
    [7, 1.03, 7.21],
  ])('%i tokens a %f cuestan %f, con un solo redondeo hacia arriba desde la mitad', (quantity, price, total) => {
    const result = tradeTotal(quantity, price);

    expect(result).toBe(total);
  });

  it('una compra de 10 con 3 tokens del superusuario paga 3 a 1,0000 y 7 a 1,0300', () => {
    const estimate = estimateBuy(10, 3, 1.03);

    expect(estimate).toEqual({ atInitialPrice: 3, atQuotePrice: 7, total: 10.21 });
  });

  it('una compra que el superusuario cubre entera va toda a 1,0000', () => {
    const estimate = estimateBuy(10, 100, 1.03);

    expect(estimate).toEqual({ atInitialPrice: 10, atQuotePrice: 0, total: 10 });
  });

  it('sin tokens del superusuario la compra va toda al precio vigente', () => {
    const estimate = estimateBuy(10, 0, 1.0345);

    expect(estimate).toEqual({ atInitialPrice: 0, atQuotePrice: 10, total: 10.35 });
  });

  it('una cantidad 0 no cuesta nada', () => {
    const estimates = [estimateBuy(0, 3, 1.03), estimateSell(0, 1.03)];

    expect(estimates).toEqual([
      { atInitialPrice: 0, atQuotePrice: 0, total: 0 },
      { atInitialPrice: 0, atQuotePrice: 0, total: 0 },
    ]);
  });

  it('una venta se estima al precio vigente', () => {
    const estimate = estimateSell(5, 1.05);

    expect(estimate).toEqual({ atInitialPrice: 0, atQuotePrice: 5, total: 5.25 });
  });

  it('una ganancia lleva signo más y una pérdida, signo menos', () => {
    const texts = [formatSigned(0.3), formatSigned(-0.3)];

    expect(texts).toEqual(['+0,30', '-0,30']);
  });

  it('el cero no lleva signo', () => {
    const texts = [formatSigned(0), formatSignedPercent(0)];

    expect(texts).toEqual(['0,00', '0,00 %']);
  });

  it('la rentabilidad lleva signo y el símbolo de porcentaje', () => {
    const texts = [formatSignedPercent(3), formatSignedPercent(-1.98)];

    expect(texts).toEqual(['+3,00 %', '-1,98 %']);
  });

  it('la ganancia o la pérdida se dicen con palabras', () => {
    const texts = [profitText(0.3), profitText(-0.3), profitText(0)];

    expect(texts).toEqual(['gana', 'pierde', 'sin cambios']);
  });

  it('el score se muestra con coma y los decimales que tenga, al menos dos', () => {
    const texts = [formatScore(0.3), formatScore(0), formatScore(-0.25), formatScore(0.123456)];

    expect(texts).toEqual(['0,30', '0,00', '-0,25', '0,123456']);
  });

  it('un total estimado se escribe en créditos, sin símbolos de moneda', () => {
    const text = formatCredits(estimateBuy(10, 3, 1.03).total);

    expect(text).toBe('10,21 cr.');
    expect(text).not.toMatch(CURRENCY_SYMBOLS);
  });

  it('un jugador inactivo o sin cotización no se opera, y eso pesa más que la venta bloqueada', () => {
    const blocks = [tradeBlockOf(false, true, 0), tradeBlockOf(true, false, 0), tradeBlockOf(false, false, 87)];

    expect(blocks).toEqual([
      { kind: 'not-tradable', reason: 'el jugador está inactivo' },
      { kind: 'not-tradable', reason: 'el jugador todavía no tiene cotización' },
      { kind: 'not-tradable', reason: 'el jugador está inactivo' },
    ]);
  });

  it('mientras el administrador tenga tokens solo se compra, y con la inicial agotada se opera libre', () => {
    const blocked = tradeBlockOf(true, true, 1);
    const free = tradeBlockOf(true, true, 0);

    expect(blocked).toEqual({ kind: 'sale-blocked', remainingTokens: 1 });
    expect(free).toBeNull();
  });

  it('los tokens se cuentan en singular y en plural', () => {
    const texts = [formatTokens(1), formatTokens(0), formatTokens(10)];

    expect(texts).toEqual(['1 token', '0 tokens', '10 tokens']);
  });
});
