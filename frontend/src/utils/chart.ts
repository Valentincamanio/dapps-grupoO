// Geometría del gráfico de evolución (research D24). Todo en porcentajes del área del gráfico,
// así el SVG (viewBox 0 0 100 100) y los botones de cada punto usan las mismas coordenadas.
import type { ChartPoint, Quote } from '../types/quotes';
import { lastPerWeek } from './quotes';

export interface ChartMargins {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface ValueDomain {
  min: number;
  max: number;
}

export interface ScaledPoint {
  // 0 a 100, de izquierda a derecha
  x: number;
  // 0 a 100, de arriba hacia abajo (el valor más alto queda arriba)
  y: number;
  point: ChartPoint;
}

export interface AxisTick {
  value: number;
  y: number;
}

export const DEFAULT_MARGINS: ChartMargins = { top: 8, right: 6, bottom: 8, left: 6 };

// Holgura cuando todos los valores son iguales: 5 % del valor, al menos 1 crédito
const FLAT_PADDING_RATIO = 0.05;
const MIN_FLAT_PADDING = 1;

// Los puntos del gráfico: una cotización por semana, la última de esa semana
export function toChartPoints(quotes: Quote[]): ChartPoint[] {
  return lastPerWeek(quotes).map((quote) => ({ week: quote.weekStart, value: quote.value, quote }));
}

// Mínimo y máximo de los valores; con un solo valor o valores iguales se abre alrededor del
// valor para no dividir por cero
export function valueDomain(values: number[]): ValueDomain {
  if (values.length === 0) return { min: 0, max: 1 };
  const min = Math.min(...values);
  const max = Math.max(...values);
  if (min === max) {
    const padding = Math.max(Math.abs(min) * FLAT_PADDING_RATIO, MIN_FLAT_PADDING);
    return { min: min - padding, max: max + padding };
  }
  return { min, max };
}

function scaleY(value: number, domain: ValueDomain, margins: ChartMargins): number {
  const height = 100 - margins.top - margins.bottom;
  const ratio = (value - domain.min) / (domain.max - domain.min);
  return margins.top + (1 - ratio) * height;
}

// Un solo punto queda centrado; con varios, el primero y el último tocan los márgenes
export function scalePoints(
  points: ChartPoint[],
  margins: ChartMargins = DEFAULT_MARGINS,
): ScaledPoint[] {
  const domain = valueDomain(points.map((point) => point.value));
  const width = 100 - margins.left - margins.right;
  return points.map((point, index) => ({
    x: points.length === 1 ? margins.left + width / 2 : margins.left + (index / (points.length - 1)) * width,
    y: scaleY(point.value, domain, margins),
    point,
  }));
}

// Ticks del eje de valores, repartidos de forma pareja entre el mínimo y el máximo del dominio
export function axisTicks(
  domain: ValueDomain,
  count = 4,
  margins: ChartMargins = DEFAULT_MARGINS,
): AxisTick[] {
  if (count < 2) return [{ value: domain.max, y: scaleY(domain.max, domain, margins) }];
  const step = (domain.max - domain.min) / (count - 1);
  return Array.from({ length: count }, (_, index) => {
    const value = domain.min + step * index;
    return { value, y: scaleY(value, domain, margins) };
  });
}

// Atributo points de un <polyline> a partir de los puntos escalados
export function polylinePoints(points: ScaledPoint[]): string {
  return points.map((point) => `${round(point.x)},${round(point.y)}`).join(' ');
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
