package ar.edu.unq.desapp.futbolmarket.shared;

/**
 * Con qué credencial se autenticó un request: un token de sesión ({@code Authorization: Bearer})
 * o una clave de API ({@code X-API-Key}).
 *
 * <p>El filtro de seguridad lo deja como {@code details} de la autenticación y la auditoría de web
 * services lo escribe junto al usuario (research D6 de la 011). Vive en {@code shared/} y no en
 * {@code security/} porque {@code security/} ya depende de {@code shared/}: si la auditoría lo
 * importara desde {@code security/}, se formaría un ciclo entre paquetes (research D4).</p>
 */
public enum CredentialType {
    JWT,
    API_KEY
}
