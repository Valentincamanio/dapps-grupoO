package ar.edu.unq.desapp.futbolmarket.modelo.user.exception;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se pide el portfolio de un día anterior al registro de la cuenta (FR-041). Responde 400.
 */
public class DateBeforeRegistrationException extends BadRequestException {
    public DateBeforeRegistrationException(LocalDate day, LocalDate registrationDay) {
        super("La fecha " + day + " es anterior al registro del usuario (" + registrationDay + ").");
    }
}
