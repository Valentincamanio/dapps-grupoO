package ar.edu.unq.desapp.futbolmarket.auth.modelo;

/**
 * Resultado del registro público: el usuario creado y la clave de API que se muestra una sola
 * vez, porque no hay otra vía para volver a consultarla (FR-014).
 */
public record RegisteredUser(AppUser user, ApiKey apiKey) {
}
