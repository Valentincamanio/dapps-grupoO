package ar.edu.unq.desapp.futbolmarket.auth.modelo.exception;

import ar.edu.unq.desapp.futbolmarket.shared.UnauthorizedException;

/**
 * Se lanza cuando la credencial no es válida. Usa el mismo mensaje para un usuario inexistente y para una contraseña incorrecta, así la respuesta no revela cuál de los dos falló (FR-017 y SC-007). Responde 401.
 */
public class InvalidCredentialsException extends UnauthorizedException {

    private static final String MESSAGE = "Credenciales inválidas.";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
