import { describe, expect, it } from 'vitest';
import type { WeightsText } from '../../src/types/quotes';
import {
  checkWeightSet,
  emptyWeightsText,
  formatHundredths,
  isFormValid,
  parseWeight,
  weightErrorMessage,
  weightMapFromText,
  weightsFormState,
  weightsFromFormState,
  weightsTextFromMap,
} from '../../src/utils/weights';

function weights(values: Partial<WeightsText>): WeightsText {
  return { ...emptyWeightsText(), ...values };
}

describe('weights', () => {
  it('suma 0,1 + 0,2 + 0,7 exactamente 1,00', () => {
    const text = weights({ GOALS: '0,1', ASSISTS: '0,2', RATING: '0,7' });

    const check = checkWeightSet(text);

    expect(check.sum).toBe(100);
    expect(check.valid).toBe(true);
    expect(formatHundredths(check.sum)).toBe('1,00');
  });

  it('un juego que suma 0,95 no es válido', () => {
    const text = weights({ GOALS: '0,25', ASSISTS: '0,20', RATING: '0,50' });

    const check = checkWeightSet(text);

    expect(check.valid).toBe(false);
    expect(formatHundredths(check.sum)).toBe('0,95');
    expect(check.errors).toEqual({});
  });

  it('un juego que pasa de 1,00 no es válido', () => {
    const check = checkWeightSet(weights({ GOALS: '0,6', RATING: '0,5' }));

    expect(check.valid).toBe(false);
    expect(formatHundredths(check.sum)).toBe('1,10');
  });

  it('acepta coma o punto como separador decimal', () => {
    expect(parseWeight('0,25')).toEqual({ ok: true, hundredths: 25 });
    expect(parseWeight('0.25')).toEqual({ ok: true, hundredths: 25 });
    expect(parseWeight(',5')).toEqual({ ok: true, hundredths: 50 });
    expect(parseWeight(' 1 ')).toEqual({ ok: true, hundredths: 100 });
  });

  it('el texto vacío pesa 0', () => {
    expect(parseWeight('')).toEqual({ ok: true, hundredths: 0 });
    expect(parseWeight('   ')).toEqual({ ok: true, hundredths: 0 });
  });

  it('rechaza más de dos decimales', () => {
    const result = parseWeight('0,125');

    expect(result).toEqual({ ok: false, error: 'tooManyDecimals' });
  });

  it('los ceros del final no cuentan como decimales', () => {
    expect(parseWeight('0,100')).toEqual({ ok: true, hundredths: 10 });
  });

  it('rechaza un peso negativo', () => {
    expect(parseWeight('-0,10')).toEqual({ ok: false, error: 'negative' });
  });

  it('rechaza un texto que no es un número', () => {
    expect(parseWeight('abc')).toEqual({ ok: false, error: 'invalid' });
    expect(parseWeight('0,1,2')).toEqual({ ok: false, error: 'invalid' });
    expect(parseWeight(',')).toEqual({ ok: false, error: 'invalid' });
  });

  it('un peso inválido marca su métrica y deja el juego inválido aunque el resto sume 1,00', () => {
    const text = weights({ GOALS: '0,5', ASSISTS: '0,5', RATING: '-0,1' });

    const check = checkWeightSet(text);

    expect(check.errors).toEqual({ RATING: 'negative' });
    expect(check.sum).toBe(100);
    expect(check.valid).toBe(false);
  });

  it('explica cada error en español', () => {
    expect(weightErrorMessage('invalid')).toBe('no es un número');
    expect(weightErrorMessage('negative')).toBe('no puede ser negativo');
    expect(weightErrorMessage('tooManyDecimals')).toBe('admite a lo sumo dos decimales');
  });

  it('un juego vacío no es válido', () => {
    expect(checkWeightSet(emptyWeightsText())).toEqual({ sum: 0, errors: {}, valid: false });
  });

  it('pasa del texto a los pesos de la API sin los vacíos ni los ceros', () => {
    const text = weights({ GOALS: '0,1', ASSISTS: '0', RATING: '0.9' });

    const map = weightMapFromText(text);

    expect(map).toEqual({ GOALS: 0.1, RATING: 0.9 });
  });

  it('pasa de los pesos de la API al texto con coma y dos decimales', () => {
    const text = weightsTextFromMap({ GOALS: 0.3, RATING: 0.7 });

    expect(text.GOALS).toBe('0,30');
    expect(text.RATING).toBe('0,70');
    expect(text.ASSISTS).toBe('');
  });

  it('un formulario es válido solo si el por defecto y cada juego por posición suman 1,00', () => {
    const state = weightsFormState({
      defaultWeights: { GOALS: 0.5, RATING: 0.5 },
      positionWeights: { GOALKEEPER: { RATING: 0.6, CLEAN_SHEETS: 0.4 } },
    });
    expect(isFormValid(state)).toBe(true);

    state.positionWeights.GOALKEEPER!.CLEAN_SHEETS = '0,3';

    expect(isFormValid(state)).toBe(false);
  });

  it('sin juegos por posición no envía positionWeights', () => {
    const state = weightsFormState();
    state.defaultWeights.GOALS = '1';

    const input = weightsFromFormState(state);

    expect(input).toEqual({ defaultWeights: { GOALS: 1 } });
  });

  it('ida y vuelta entre el formulario y los pesos de la API conserva los juegos', () => {
    const weightsOfVersion = {
      defaultWeights: { GOALS: 0.25, ASSISTS: 0.15, RATING: 0.6 },
      positionWeights: { FORWARD: { GOALS: 0.4, SHOTS_ON_TARGET: 0.6 } },
    };

    const input = weightsFromFormState(weightsFormState(weightsOfVersion));

    expect(input).toEqual(weightsOfVersion);
  });
});
