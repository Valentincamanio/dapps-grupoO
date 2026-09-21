package ar.edu.unq.desapp.futbolmarket.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro único que acepta las dos credenciales del sistema (research D6).
 *
 * <p>No se anota con {@code @Component}: Spring Boot registra cualquier bean {@code Filter} como
 * filtro del servlet y este correría además fuera de la cadena de seguridad. Lo construye
 * {@link SecurityConfig}.</p>
 *
 * <p>Precedencia: si llega un {@code Authorization: Bearer}, se evalúa solo el JWT y la
 * {@code X-API-Key} se ignora aunque sea válida; si no, se evalúa la clave; si no hay ninguna, el
 * request sigue como anónimo y responde el entry point. Un {@code Authorization} de otro esquema,
 * por ejemplo Basic, no es una credencial de este sistema y se ignora.</p>
 *
 * <p>{@code shouldNotFilter} usa el mismo matcher de rutas públicas que la autorización, así una
 * credencial inválida no bloquea un recurso público.</p>
 */
public class CredentialAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String UNKNOWN_CREDENTIAL_MESSAGE = "La credencial no corresponde a ningún usuario.";

    private final JwtService jwtService;
    private final AuthService authService;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final RequestMatcher publicEndpoints;

    public CredentialAuthenticationFilter(JwtService jwtService, AuthService authService,
                                          AuthenticationEntryPoint authenticationEntryPoint,
                                          RequestMatcher publicEndpoints) {
        this.jwtService = jwtService;
        this.authService = authService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publicEndpoints.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            resolveUser(request).ifPresent(this::authenticate);
        } catch (AuthenticationException e) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, e);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Vacío significa request anónimo; una credencial que no resuelve lanza la excepción. */
    private Optional<AppUser> resolveUser(HttpServletRequest request) {
        String token = bearerToken(request);
        if (token != null) {
            return Optional.of(authService.findById(jwtService.parseUserId(token))
                    .orElseThrow(CredentialAuthenticationFilter::unknownCredential));
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return Optional.of(authService.findByApiKey(apiKey)
                    .orElseThrow(CredentialAuthenticationFilter::unknownCredential));
        }

        return Optional.empty();
    }

    /**
     * El mensaje no llega al cliente: el entry point responde siempre el texto fijo que
     * corresponde al tipo de excepción, así el rechazo no revela qué parte falló (FR-021).
     */
    private static BadCredentialsException unknownCredential() {
        return new BadCredentialsException(UNKNOWN_CREDENTIAL_MESSAGE);
    }

    private void authenticate(AppUser user) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getId(),
                null,
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + user.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }
}
