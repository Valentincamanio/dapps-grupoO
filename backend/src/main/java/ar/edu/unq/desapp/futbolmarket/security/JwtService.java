package ar.edu.unq.desapp.futbolmarket.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.SessionToken;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.SessionTokenIssuer;
import ar.edu.unq.desapp.futbolmarket.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Emite los tokens de sesión, en HS256 y con el id del usuario en {@code sub} (research D3).
 *
 * <p>El algoritmo se declara explícito: sin eso, jjwt lo elige según el tamaño de la clave y un
 * secreto de 64 bytes terminaría firmando con HS512. La clave se arma en el constructor, así un
 * secreto de menos de 256 bits corta el arranque con {@code WeakKeyException} y no falla recién
 * en el primer login.</p>
 *
 * <p>La hora sale de un {@link Clock} inyectado, lo que permite testear el vencimiento sin
 * esperar. El instante de emisión se trunca a segundos porque el JWT guarda {@code iat} y
 * {@code exp} con esa precisión: así el {@code expiresAt} que se devuelve es exactamente el que
 * viaja en el token.</p>
 */
@Component
public class JwtService implements SessionTokenIssuer {

    private final SecretKey secretKey;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(JwtProperties jwtProperties, Clock clock) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        this.expiration = jwtProperties.expiration();
        this.clock = clock;
    }

    @Override
    public SessionToken issueFor(AppUser user) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(expiration);

        String value = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();

        return new SessionToken(value, expiresAt);
    }
}
