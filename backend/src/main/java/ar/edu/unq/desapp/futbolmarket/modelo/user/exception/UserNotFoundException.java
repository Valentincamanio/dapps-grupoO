package ar.edu.unq.desapp.futbolmarket.modelo.user.exception;

import ar.edu.unq.desapp.futbolmarket.shared.NotFoundException;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException(Long userId) {
        super("No se encontró el usuario con id " + userId + ".");
    }
}
