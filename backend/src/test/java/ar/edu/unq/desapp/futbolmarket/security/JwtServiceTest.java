package ar.edu.unq.desapp.futbolmarket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.Role;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.SessionToken;
import ar.edu.unq.desapp.futbolmarket.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

class JwtServiceTest {

    private static final long USER_ID = 42L;
    private static final Duration EXPIRATION = Duration.ofHours(24);
    private static final Instant NOW = Instant.parse("2026-09-18T15:00:00Z");
    private static final String HS256 = "HS256";
    private static final int STRONG_SECRET_BYTES = 32;
    private static final int WEAK_SECRET_BYTES = 16;

    private final String secret = randomSecret(STRONG_SECRET_BYTES);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(new JwtProperties(secret, EXPIRATION), clock);

    @Test
    void elTokenSeParseaConLaMismaClaveYLoFirmaConHs256() {
        SessionToken sessionToken = jwtService.issueFor(user());

        assertThat(parse(sessionToken.value()).getHeader().getAlgorithm()).isEqualTo(HS256);
    }

    @Test
    void elSubjectEsElIdDelUsuario() {
        SessionToken sessionToken = jwtService.issueFor(user());

        assertThat(parse(sessionToken.value()).getPayload().getSubject())
                .isEqualTo(String.valueOf(USER_ID));
    }

    @Test
    void elVencimientoEsLaEmisionMasVeinticuatroHoras() {
        SessionToken sessionToken = jwtService.issueFor(user());
        Claims claims = parse(sessionToken.value()).getPayload();

        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(NOW));
        assertThat(claims.getExpiration()).isEqualTo(Date.from(NOW.plus(EXPIRATION)));
    }

    @Test
    void elExpiresAtDevueltoCoincideConElDelToken() {
        SessionToken sessionToken = jwtService.issueFor(user());
        Claims claims = parse(sessionToken.value()).getPayload();

        assertThat(sessionToken.expiresAt()).isEqualTo(claims.getExpiration().toInstant());
    }

    @Test
    void unSecretoDeMenosDeDoscientosCincuentaYSeisBitsCortaLaConstruccion() {
        JwtProperties weakProperties = new JwtProperties(randomSecret(WEAK_SECRET_BYTES), EXPIRATION);

        assertThatThrownBy(() -> new JwtService(weakProperties, clock))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void unTokenVigenteDevuelveElIdDelUsuario() {
        SessionToken sessionToken = jwtService.issueFor(user());

        assertThat(jwtService.parseUserId(sessionToken.value())).isEqualTo(USER_ID);
    }

    @Test
    void unSegundoAntesDelVencimientoElTokenSigueValiendo() {
        String token = jwtService.issueFor(user()).value();
        JwtService atExpiration = serviceAt(NOW.plus(EXPIRATION).minusSeconds(1));

        assertThat(atExpiration.parseUserId(token)).isEqualTo(USER_ID);
    }

    @Test
    void unSegundoDespuesDelVencimientoElTokenSeRechazaComoVencido() {
        String token = jwtService.issueFor(user()).value();
        JwtService afterExpiration = serviceAt(NOW.plus(EXPIRATION).plusSeconds(1));

        assertThatThrownBy(() -> afterExpiration.parseUserId(token))
                .isInstanceOf(CredentialsExpiredException.class);
    }

    @Test
    void unTokenConLaFirmaAlteradaSeRechazaComoInvalido() {
        String token = jwtService.issueFor(user()).value();

        assertThatThrownBy(() -> jwtService.parseUserId(tamperSignature(token)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechazaComoInvalido() {
        JwtService otherService = new JwtService(
                new JwtProperties(randomSecret(STRONG_SECRET_BYTES), EXPIRATION), clock);
        String foreignToken = otherService.issueFor(user()).value();

        assertThatThrownBy(() -> jwtService.parseUserId(foreignToken))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void unaCadenaQueNoEsUnTokenSeRechazaComoInvalida() {
        assertThatThrownBy(() -> jwtService.parseUserId("abc"))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> jwtService.parseUserId(""))
                .isInstanceOf(BadCredentialsException.class);
    }

    private JwtService serviceAt(Instant instant) {
        return new JwtService(new JwtProperties(secret, EXPIRATION),
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    /** Cambia el último carácter de la firma, dejando intactos el header y el payload. */
    private String tamperSignature(String token) {
        char last = token.charAt(token.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        return token.substring(0, token.length() - 1) + replacement;
    }

    private Jws<Claims> parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        return Jwts.parser().verifyWith(key).clock(() -> Date.from(NOW)).build().parseSignedClaims(token);
    }

    private AppUser user() {
        return AppUser.reconstitute(USER_ID, "lionel10", "lionel@correo.com", "hash", null,
                Role.USER, new BigDecimal("1000.00"));
    }

    private static String randomSecret(int bytes) {
        byte[] material = new byte[bytes];
        new SecureRandom().nextBytes(material);
        return Base64.getEncoder().encodeToString(material);
    }
}
