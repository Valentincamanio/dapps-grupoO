package ar.edu.unq.desapp.futbolmarket.auth.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.RegisteredUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.RegistrationAvailability;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.SessionToken;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.SessionTokenIssuer;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.config.AuthProperties;

/**
 * Orquesta el registro y el inicio de sesión: consulta el repository, delega las decisiones en el
 * modelo y persiste.
 *
 * <p>No lleva {@code @Transactional}: cada operación del repository abre la suya, y el índice
 * único es la última línea de defensa ante dos registros simultáneos.</p>
 */
@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordHasher passwordHasher;
    private final SessionTokenIssuer sessionTokenIssuer;
    private final AuthProperties authProperties;

    /**
     * El hash descartable se calcula una sola vez, sobre un valor al azar que nunca va a coincidir
     * con ninguna contraseña. No es un secreto: solo sirve para que el login de un usuario
     * inexistente pague el mismo costo de BCrypt que el de uno existente.
     */
    private final String discardablePasswordHash;

    public AuthService(AppUserRepository appUserRepository, PasswordHasher passwordHasher,
                       SessionTokenIssuer sessionTokenIssuer, AuthProperties authProperties) {
        this.appUserRepository = appUserRepository;
        this.passwordHasher = passwordHasher;
        this.sessionTokenIssuer = sessionTokenIssuer;
        this.authProperties = authProperties;
        this.discardablePasswordHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    public RegisteredUser register(String username, String email, String rawPassword) {
        new RegistrationAvailability(
                appUserRepository.existsByUsername(username),
                appUserRepository.existsByEmail(email))
                .ensureAvailable();

        RegisteredUser registered = AppUser.register(username, email, rawPassword,
                authProperties.initialBalance(), passwordHasher);
        return new RegisteredUser(appUserRepository.save(registered.user()), registered.apiKey());
    }

    /**
     * Un usuario inexistente y una contraseña incorrecta se rechazan igual, con el mismo mensaje y
     * en un tiempo parecido: si el usuario no existe, se verifica igual contra el hash descartable
     * para que la demora no delate su existencia (research D5 y SC-007).
     */
    public SessionToken login(String username, String rawPassword) {
        Optional<AppUser> found = appUserRepository.findByUsername(username);
        if (found.isEmpty()) {
            passwordHasher.matches(rawPassword, discardablePasswordHash);
            throw new InvalidCredentialsException();
        }

        AppUser user = found.get();
        user.verifyPassword(rawPassword, passwordHasher);
        return sessionTokenIssuer.issueFor(user);
    }
}
