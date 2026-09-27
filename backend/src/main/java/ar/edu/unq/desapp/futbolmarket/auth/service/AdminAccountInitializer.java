package ar.edu.unq.desapp.futbolmarket.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.PasswordHasher;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateEmailException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.DuplicateUsernameException;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidUserDataException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.config.AuthProperties;
import lombok.RequiredArgsConstructor;

/**
 * Alta del administrador inicial al arrancar (research D12).
 *
 * <p>Corre en todos los perfiles; lo único que la habilita es que la configuración esté completa.
 * Si falta algo o es inválido, advierte y la aplicación levanta igual (FR-038). Es idempotente:
 * si el nombre de usuario ya existe, no toca nada, aunque cambien las variables (FR-039).</p>
 *
 * <p>No es {@code @Transactional} a propósito: si capturara una excepción de dominio dentro de una
 * transacción marcada rollback-only, terminaría en {@code UnexpectedRollbackException}. Cada
 * operación del repository abre la suya.</p>
 *
 * <p>Ningún log incluye la contraseña configurada.</p>
 */
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AuthProperties authProperties;
    private final AppUserRepository appUserRepository;
    private final PasswordHasher passwordHasher;

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.Admin admin = authProperties.admin();
        if (isIncomplete(admin) || isAlreadyTaken(admin)) {
            return;
        }
        create(admin);
    }

    /** Informa qué falta, si falta algo. La contraseña se revisa primero (FR-038). */
    private boolean isIncomplete(AuthProperties.Admin admin) {
        if (admin == null || isBlank(admin.password())) {
            LOGGER.warn("Falta la contraseña del administrador (FUTBOLMARKET_AUTH_ADMIN_PASSWORD): "
                    + "se omite el alta.");
            return true;
        }
        if (isBlank(admin.username()) || isBlank(admin.email())) {
            LOGGER.warn("Falta el nombre de usuario o el correo del administrador: se omite el alta.");
            return true;
        }
        return false;
    }

    /**
     * Un username existente, aunque sea de un usuario común, no es un error: es el caso normal de
     * cada arranque después del primero, y por eso va en INFO (FR-039).
     */
    private boolean isAlreadyTaken(AuthProperties.Admin admin) {
        if (appUserRepository.existsByUsername(admin.username())) {
            LOGGER.info("Ya existe una cuenta con el nombre de usuario '{}': el administrador no se modifica.",
                    admin.username());
            return true;
        }
        if (appUserRepository.existsByEmail(admin.email())) {
            LOGGER.warn("El correo del administrador ya está registrado en otra cuenta: se omite el alta.");
            return true;
        }
        return false;
    }

    /**
     * El mensaje de {@link InvalidUserDataException} es el de la regla incumplida y nunca repite el
     * valor, así que se puede registrar sin exponer la contraseña. Los {@code Duplicate*} solo
     * aparecen si un registro público gana la carrera entre la verificación y el guardado.
     */
    private void create(AuthProperties.Admin admin) {
        try {
            AppUser saved = appUserRepository.save(
                    AppUser.createAdmin(admin.username(), admin.email(), admin.password(), passwordHasher));
            LOGGER.info("Se creó la cuenta de administrador '{}'.", saved.getUsername());
        } catch (InvalidUserDataException e) {
            LOGGER.warn("La configuración del administrador no es válida: {} Se omite el alta.", e.getMessage());
        } catch (DuplicateUsernameException | DuplicateEmailException e) {
            LOGGER.warn("Otra cuenta tomó el nombre de usuario o el correo del administrador mientras se "
                    + "creaba: se omite el alta.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
