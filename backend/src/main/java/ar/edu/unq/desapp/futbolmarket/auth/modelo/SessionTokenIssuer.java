package ar.edu.unq.desapp.futbolmarket.auth.modelo;

/**
 * Puerto del modelo para emitir tokens de sesión. Lo implementa {@code security/JwtService}, así
 * el servicio emite tokens sin depender de {@code security/} y no se forma un ciclo entre los dos
 * paquetes (research D3).
 */
public interface SessionTokenIssuer {

    SessionToken issueFor(AppUser user);
}
