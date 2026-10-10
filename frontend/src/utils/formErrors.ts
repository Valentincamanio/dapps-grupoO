// Reparto de errores de la API entre los campos de un formulario (data-model §1).
import type { ApiError } from '../types/api';

export interface FormErrors {
  // Mensaje por nombre de campo
  fields: Record<string, string>;
  // Nota general del formulario
  general?: string;
}

export function toFormErrors(apiError: ApiError, fields: string[]): FormErrors {
  const result: FormErrors = { fields: {} };

  if (apiError.violations && apiError.violations.length > 0) {
    for (const violation of apiError.violations) {
      if (fields.includes(violation.field) && !result.fields[violation.field]) {
        result.fields[violation.field] = violation.message;
      }
    }
    // Una violación de un campo que el formulario no tiene no se pierde
    const orphan = apiError.violations.find((v) => !fields.includes(v.field));
    if (orphan) result.general = orphan.message;
    return result;
  }

  result.general = apiError.message;

  // Los 409 del registro no traen violations: se deduce el campo del mensaje
  if (apiError.status === 409) {
    const message = apiError.message.toLowerCase();
    if (fields.includes('username') && message.includes('usuario')) {
      result.fields.username = apiError.message;
    }
    if (fields.includes('email') && message.includes('correo')) {
      result.fields.email = apiError.message;
    }
  }

  return result;
}
