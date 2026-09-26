package ar.edu.unq.desapp.futbolmarket.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.SessionToken;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.SessionTokenIssuer;
import ar.edu.unq.desapp.futbolmarket.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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

    private static final String EXPIRED_TOKEN_MESSAGE = "El token de sesión está vencido.";
    private static final String INVALID_TOKEN_MESSAGE = "El token de sesión es inválido.";

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

    /**
     * Valida la firma y el vencimiento, y devuelve el id que viaja en {@code sub}.
     *
     * <p>El margen de reloj es cero porque el token tiene que dejar de valer apenas pasado su
     * vencimiento. Se distinguen solo dos casos, que son los dos mensajes que ve el cliente: el
     * token vencido y todo lo demás, incluido un {@code sub} que no sea un id.</p>
     */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .clock(() -> Date.from(clock.instant()))
                    .clockSkewSeconds(0)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (ExpiredJwtException e) {
            throw new CredentialsExpiredException(EXPIRED_TOKEN_MESSAGE, e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException(INVALID_TOKEN_MESSAGE, e);
        }
    }
}
