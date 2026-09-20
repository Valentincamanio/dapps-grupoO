package ar.edu.unq.desapp.futbolmarket.auth.service;

import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.RegisteredUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.RegistrationAvailability;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.config.AuthProperties;
import lombok.RequiredArgsConstructor;

/**
 * Orquesta el registro: consulta la disponibilidad, delega la decisión en el modelo y persiste.
 *
 * <p>No lleva {@code @Transactional}: cada operación del repository abre la suya, y el índice
 * único es la última línea de defensa ante dos registros simultáneos.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordHasher passwordHasher;
    private final AuthProperties authProperties;

    public RegisteredUser register(String username, String email, String rawPassword) {
        new RegistrationAvailability(
                appUserRepository.existsByUsername(username),
                appUserRepository.existsByEmail(email))
                .ensureAvailable();

        RegisteredUser registered = AppUser.register(username, email, rawPassword,
                authProperties.initialBalance(), passwordHasher);
        return new RegisteredUser(appUserRepository.save(registered.user()), registered.apiKey());
    }
}
