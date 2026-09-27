package ar.edu.unq.desapp.futbolmarket.auth.controller.dto;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.RegisteredUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;

/**
 * Respuesta del registro. Es el único lugar donde viaja la clave de API en claro; no lleva la
 * contraseña ni ninguno de los dos hashes.
 */
public record RegisterResponse(Long id, String username, String email, Role role, BigDecimal balance,
                               String apiKey) {

    public static RegisterResponse from(RegisteredUser registeredUser) {
        AppUser user = registeredUser.user();
        return new RegisterResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getBalance(),
                registeredUser.apiKey().value());
    }
}
