package ar.edu.unq.desapp.futbolmarket.service.market;

import ar.edu.unq.desapp.futbolmarket.modelo.auth.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.exception.UserNotFoundException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;

/**
 * El permiso para leer los datos de una cuenta (research D24), común a las consultas de
 * {@code /users/{id}/...}. El permiso se verifica antes de buscar la cuenta pedida: un usuario común
 * recibe 403 tanto por una cuenta ajena como por una inexistente, y el administrador recibe 404 por
 * una inexistente.
 */
final class AccountAccess {

    private AccountAccess() {
    }

    /**
     * La cuenta {@code userId}, si {@code requesterId} puede leerla. Quien pide sale de la credencial:
     * si ya no existe, la credencial no vale (401).
     */
    static AppUser accountOf(AppUserRepository userRepository, long requesterId, long userId) {
        AppUser requester = userRepository.findById(requesterId).orElseThrow(InvalidCredentialsException::new);
        requester.requireCanAccessAccountOf(userId);
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }
}
