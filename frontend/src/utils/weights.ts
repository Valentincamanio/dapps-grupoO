// Pesos de una estrategia (research D24, FR-066). La suma se hace en centésimos enteros, sin
// float: 0,1 + 0,2 + 0,7 tiene que dar exactamente 1,00. Las reglas son las del backend
// (WeightSet): cada peso >= 0, a lo sumo dos decimales y el juego suma 1,00.
import type { Position } from '../types/api';
import { POSITIONS } from '../types/api';
import {
  METRICS,
  type Metric,
  type PositionWeights,
  type StrategyInput,
  type WeightMap,
  type WeightsFormState,
  type WeightsText,
} from '../types/quotes';

export type WeightError = 'invalid' | 'negative' | 'tooManyDecimals';

export type ParsedWeight = { ok: true; hundredths: number } | { ok: false; error: WeightError };

// Un juego suma exactamente 1,00
export const FULL_SET_HUNDREDTHS = 100;

const MAX_DECIMALS = 2;
const HUNDREDTHS_PER_UNIT = 100;

// Signo opcional, parte entera y decimales con coma o punto; al menos un dígito
const DECIMAL_TEXT = /^(-?)(\d*)(?:[.,](\d*))?$/;

const WEIGHT_ERROR_MESSAGES: Record<WeightError, string> = {
  invalid: 'no es un número',
  negative: 'no puede ser negativo',
  tooManyDecimals: 'admite a lo sumo dos decimales',
};

// Texto de un peso -> centésimos enteros. El texto vacío pesa 0 (la métrica no figura).
export function parseWeight(text: string): ParsedWeight {
  const trimmed = text.trim();
  if (trimmed === '') return { ok: true, hundredths: 0 };

  const match = DECIMAL_TEXT.exec(trimmed);
  if (!match) return { ok: false, error: 'invalid' };
  const [, sign, whole, rawFraction = ''] = match;
  if (whole === '' && rawFraction === '') return { ok: false, error: 'invalid' };

  // Los ceros del final no cuentan como decimales (0,100 vale como 0,10)
  const fraction = rawFraction.replace(/0+$/, '');
  const hundredths =
    Number(whole || '0') * HUNDREDTHS_PER_UNIT + Number(fraction.padEnd(MAX_DECIMALS, '0').slice(0, MAX_DECIMALS));
  if (!Number.isSafeInteger(hundredths)) return { ok: false, error: 'invalid' };
  if (sign === '-' && (hundredths > 0 || fraction.length > 0)) return { ok: false, error: 'negative' };
  if (fraction.length > MAX_DECIMALS) return { ok: false, error: 'tooManyDecimals' };
  return { ok: true, hundredths };
}

export function weightErrorMessage(error: WeightError): string {
  return WEIGHT_ERROR_MESSAGES[error];
}

// Centésimos -> texto con coma y dos decimales, p. ej. 95 -> 0,95
export function formatHundredths(hundredths: number): string {
  const units = Math.trunc(hundredths / HUNDREDTHS_PER_UNIT);
  const cents = String(Math.abs(hundredths % HUNDREDTHS_PER_UNIT)).padStart(MAX_DECIMALS, '0');
  return `${units},${cents}`;
}

export interface WeightSetCheck {
  // Suma de los pesos válidos, en centésimos
  sum: number;
  // Las métricas con un texto inválido y por qué
  errors: Partial<Record<Metric, WeightError>>;
  // Sin errores y con suma exactamente 1,00
  valid: boolean;
}

export function checkWeightSet(text: WeightsText): WeightSetCheck {
  let sum = 0;
  const errors: WeightSetCheck['errors'] = {};
  for (const metric of METRICS) {
    const parsed = parseWeight(text[metric]);
    if (parsed.ok) {
      sum += parsed.hundredths;
    } else {
      errors[metric] = parsed.error;
    }
  }
  const valid = Object.keys(errors).length === 0 && sum === FULL_SET_HUNDREDTHS;
  return { sum, errors, valid };
}

// El por defecto y cada juego por posición definido suman exactamente 1,00
export function isFormValid(state: WeightsFormState): boolean {
  return (
    checkWeightSet(state.defaultWeights).valid &&
    definedPositions(state).every((position) => checkWeightSet(state.positionWeights[position]!).valid)
  );
}

// Las posiciones con juego propio, en el orden de POSITIONS
export function definedPositions(state: WeightsFormState): Position[] {
  return POSITIONS.filter((position) => state.positionWeights[position] !== undefined);
}

export function emptyWeightsText(): WeightsText {
  return Object.fromEntries(METRICS.map((metric) => [metric, ''])) as WeightsText;
}

// Pesos de la API -> texto del formulario; las métricas que no figuran quedan vacías
export function weightsTextFromMap(map: WeightMap): WeightsText {
  const text = emptyWeightsText();
  for (const metric of METRICS) {
    const weight = map[metric];
    if (weight !== undefined && weight !== 0) {
      text[metric] = formatHundredths(Math.round(weight * HUNDREDTHS_PER_UNIT));
    }
  }
  return text;
}

// Texto del formulario -> pesos de la API; solo viajan los válidos mayores que 0
export function weightMapFromText(text: WeightsText): WeightMap {
  const map: WeightMap = {};
  for (const metric of METRICS) {
    const parsed = parseWeight(text[metric]);
    if (parsed.ok && parsed.hundredths > 0) {
      map[metric] = parsed.hundredths / HUNDREDTHS_PER_UNIT;
    }
  }
  return map;
}

// Estado inicial del formulario: vacío al crear, o los pesos de una versión al editar
export function weightsFormState(weights?: Pick<StrategyInput, 'defaultWeights' | 'positionWeights'>): WeightsFormState {
  if (!weights) return { defaultWeights: emptyWeightsText(), positionWeights: {} };
  const positionWeights: WeightsFormState['positionWeights'] = {};
  for (const position of POSITIONS) {
    const map = weights.positionWeights?.[position];
    if (map) positionWeights[position] = weightsTextFromMap(map);
  }
  return { defaultWeights: weightsTextFromMap(weights.defaultWeights), positionWeights };
}

// Estado del formulario -> los pesos del pedido; sin juegos por posición no viaja positionWeights
export function weightsFromFormState(
  state: WeightsFormState,
): Pick<StrategyInput, 'defaultWeights' | 'positionWeights'> {
  const positions = definedPositions(state);
  if (positions.length === 0) return { defaultWeights: weightMapFromText(state.defaultWeights) };
  const positionWeights: PositionWeights = {};
  for (const position of positions) {
    positionWeights[position] = weightMapFromText(state.positionWeights[position]!);
  }
  return { defaultWeights: weightMapFromText(state.defaultWeights), positionWeights };
}

// Parámetros de valuación: el factor de escala y la cotización mínima junto con los pesos. Los
// dos números siguen la regla del backend: mayores que 0 (el mínimo, al menos 0,01, que con dos
// decimales es lo mismo) y a lo sumo dos decimales.

export interface ParametersFormState {
  scaleFactor: string;
  minimumValue: string;
  weights: WeightsFormState;
}

export interface ParametersCheck {
  scaleFactorError?: string;
  minimumValueError?: string;
  // Los dos números válidos y todos los juegos definidos sumando 1,00
  valid: boolean;
}

// Los de una estrategia nueva cuando no se los indica (contracts/strategies-api.yaml)
const DEFAULT_SCALE_FACTOR_TEXT = '10';
const DEFAULT_MINIMUM_VALUE_TEXT = '10,00';

// Mensaje del error de un número positivo con dos decimales, o undefined si es válido
export function positiveAmountError(text: string): string | undefined {
  if (text.trim() === '') return 'es obligatorio';
  const parsed = parseWeight(text);
  if (!parsed.ok) return weightErrorMessage(parsed.error);
  if (parsed.hundredths === 0) return 'tiene que ser mayor que 0';
  return undefined;
}

// Texto ya validado -> número de la API
function amountFromText(text: string): number {
  const parsed = parseWeight(text);
  return parsed.ok ? parsed.hundredths / HUNDREDTHS_PER_UNIT : Number.NaN;
}

// Vacío con los valores por defecto al crear, o los de una versión al editar
export function parametersFormState(version?: StrategyInput): ParametersFormState {
  if (!version) {
    return {
      scaleFactor: DEFAULT_SCALE_FACTOR_TEXT,
      minimumValue: DEFAULT_MINIMUM_VALUE_TEXT,
      weights: weightsFormState(),
    };
  }
  return {
    scaleFactor: amountText(version.scaleFactor, DEFAULT_SCALE_FACTOR_TEXT),
    minimumValue: amountText(version.minimumValue, DEFAULT_MINIMUM_VALUE_TEXT),
    weights: weightsFormState(version),
  };
}

// Número de la API -> texto con coma y dos decimales; sin número, el valor por defecto
function amountText(amount: number | undefined, fallback: string): string {
  return amount === undefined ? fallback : formatHundredths(Math.round(amount * HUNDREDTHS_PER_UNIT));
}

export function checkParameters(state: ParametersFormState): ParametersCheck {
  const scaleFactorError = positiveAmountError(state.scaleFactor);
  const minimumValueError = positiveAmountError(state.minimumValue);
  return {
    scaleFactorError,
    minimumValueError,
    valid: !scaleFactorError && !minimumValueError && isFormValid(state.weights),
  };
}

// Estado del formulario (ya validado) -> parámetros del pedido
export function parametersFromFormState(state: ParametersFormState): StrategyInput {
  return {
    scaleFactor: amountFromText(state.scaleFactor),
    minimumValue: amountFromText(state.minimumValue),
    ...weightsFromFormState(state.weights),
  };
}
