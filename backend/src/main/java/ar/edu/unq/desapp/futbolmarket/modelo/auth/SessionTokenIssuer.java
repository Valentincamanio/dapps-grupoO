package ar.edu.unq.desapp.futbolmarket.modelo.auth;

import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;

/**
 * Puerto del modelo para emitir tokens de sesión. Lo implementa {@code security/JwtService}, así
 * el servicio emite tokens sin depender de {@code security/} y no se forma un ciclo entre los dos
 * paquetes (research D3).
 */
public interface SessionTokenIssuer {

    SessionToken issueFor(AppUser user);
}
