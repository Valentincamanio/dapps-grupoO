import { describe, expect, it } from 'vitest';
import type { ApiError } from '../../src/types/api';
import { toFormErrors } from '../../src/utils/formErrors';

const FIELDS = ['username', 'email', 'password'];

function apiError(overrides: Partial<ApiError>): ApiError {
  return {
    timestamp: '2026-01-01T00:00:00Z',
    status: 400,
    error: 'Bad Request',
    message: 'El request tiene datos inválidos.',
    path: '/auth/register',
    ...overrides,
  };
}

describe('toFormErrors', () => {
  it('reparte las violations en el campo de cada una', () => {
    const errors = toFormErrors(
      apiError({
        violations: [
          { field: 'username', message: 'usuario corto' },
          { field: 'password', message: 'contraseña corta' },
        ],
      }),
      FIELDS,
    );

    expect(errors.fields).toEqual({ username: 'usuario corto', password: 'contraseña corta' });
    expect(errors.general).toBeUndefined();
  });

  it('conserva la primera violation cuando un campo trae varias', () => {
    const errors = toFormErrors(
      apiError({
        violations: [
          { field: 'email', message: 'primera' },
          { field: 'email', message: 'segunda' },
        ],
      }),
      FIELDS,
    );

    expect(errors.fields.email).toBe('primera');
  });

  it('una violation de un campo que el formulario no tiene va al mensaje general', () => {
    const errors = toFormErrors(
      apiError({ violations: [{ field: 'otroCampo', message: 'no es de este formulario' }] }),
      FIELDS,
    );

    expect(errors.fields).toEqual({});
    expect(errors.general).toBe('no es de este formulario');
  });

  it('sin violations deja el mensaje de la API como mensaje general', () => {
    const errors = toFormErrors(
      apiError({ status: 500, error: 'Internal Server Error', message: 'algo falló' }),
      FIELDS,
    );

    expect(errors.fields).toEqual({});
    expect(errors.general).toBe('algo falló');
  });

  it('el 409 de usuario queda asociado al campo usuario', () => {
    const errors = toFormErrors(
      apiError({ status: 409, message: 'El nombre de usuario ya está registrado.' }),
      FIELDS,
    );

    expect(errors.fields.username).toBe('El nombre de usuario ya está registrado.');
    expect(errors.fields.email).toBeUndefined();
  });

  it('el 409 de correo queda asociado al campo correo', () => {
    const errors = toFormErrors(
      apiError({ status: 409, message: 'El correo electrónico ya está registrado.' }),
      FIELDS,
    );

    expect(errors.fields.email).toBe('El correo electrónico ya está registrado.');
    expect(errors.fields.username).toBeUndefined();
  });

  it('el 409 de usuario y correo marca los dos campos', () => {
    const errors = toFormErrors(
      apiError({
        status: 409,
        message: 'El nombre de usuario y el correo electrónico ya están registrados.',
      }),
      FIELDS,
    );

    expect(errors.fields.username).toBeDefined();
    expect(errors.fields.email).toBeDefined();
  });
});
