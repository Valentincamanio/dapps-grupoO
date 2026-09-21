package ar.edu.unq.desapp.futbolmarket.auth.service;

import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.ApiKey;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Operaciones sobre la cuenta del usuario autenticado. Carga el usuario, delega la decisión en el
 * modelo y persiste el resultado.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AppUserRepository appUserRepository;
    private final PasswordHasher passwordHasher;

    public AppUser getProfile(Long userId) {
        return authenticatedUser(userId);
    }

    /** Si el modelo rechaza el cambio, lanza antes de guardar y nada se persiste. */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = authenticatedUser(userId);
        user.changePassword(currentPassword, newPassword, passwordHasher);
        appUserRepository.save(user);
    }

    /**
     * Emite una clave nueva y la devuelve en claro, por única vez. Al guardarse el hash nuevo, la
     * clave anterior deja de valer en el acto (FR-032 a FR-034).
     */
    public ApiKey regenerateApiKey(Long userId) {
        AppUser user = authenticatedUser(userId);
        ApiKey apiKey = user.issueApiKey();
        appUserRepository.save(user);
        return apiKey;
    }

    /**
     * El filtro ya resolvió al usuario antes de llegar acá, así que un id inexistente solo puede
     * aparecer en una carrera. Se trata como credencial inválida, igual que en el filtro.
     */
    private AppUser authenticatedUser(Long userId) {
        return appUserRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
    }
}
