package ar.edu.unq.desapp.futbolmarket.shared;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Formato de error común del backend. {@code violations} viaja solo en los errores de
 * validación; en el resto se omite.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<Violation> violations
) {
    /**
     * No lleva el valor rechazado a propósito: incluirlo devolvería la contraseña enviada en un
     * 400 de registro o de cambio de contraseña (FR-009 y SC-003 del spec de auth).
     */
    public record Violation(String field, String message) {
    }
}
