package ar.edu.unq.desapp.futbolmarket.auth.controller.dto;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;

/**
 * Perfil del usuario autenticado. No lleva la contraseña, ninguno de los dos hashes ni la clave
 * de API (FR-025).
 */
public record ProfileResponse(Long id, String username, String email, Role role, BigDecimal balance) {

    public static ProfileResponse from(AppUser user) {
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getBalance());
    }
}
