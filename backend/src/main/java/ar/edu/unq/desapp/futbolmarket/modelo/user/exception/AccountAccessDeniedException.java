package ar.edu.unq.desapp.futbolmarket.modelo.user.exception;

import ar.edu.unq.desapp.futbolmarket.shared.ForbiddenException;

/**
 * Un usuario pide el portfolio, el historial o las órdenes de otra cuenta: solo los ve su dueño o
 * el administrador (FR-042 y FR-046). Responde 403.
 */
public class AccountAccessDeniedException extends ForbiddenException {

    private static final String MESSAGE = "No tiene permiso para ver la cuenta de otro usuario.";

    public AccountAccessDeniedException() {
        super(MESSAGE);
    }
}
