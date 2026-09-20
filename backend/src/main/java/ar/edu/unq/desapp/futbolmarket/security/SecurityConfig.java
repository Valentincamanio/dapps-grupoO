package ar.edu.unq.desapp.futbolmarket.security;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;

/**
 * Cadena de seguridad de la API: sin sesión, sin login por formulario y con los rechazos en JSON
 * (research D7).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;
    private final JsonAccessDeniedHandler jsonAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Las rutas públicas, en un único matcher que también consume el filtro de autenticación, así
     * la lista no se escribe dos veces. No se usa {@code /auth/**}, que dejaría abiertos
     * {@code /auth/me} y sus subrutas.
     */
    @Bean
    public RequestMatcher publicEndpoints() {
        return new OrRequestMatcher(
                pathPattern(HttpMethod.POST, "/auth/register"),
                pathPattern(HttpMethod.POST, "/auth/login"),
                pathPattern("/swagger-ui/**"),
                pathPattern("/swagger-ui.html"),
                pathPattern("/v3/api-docs/**"),
                pathPattern(HttpMethod.GET, "/actuator/health"),
                // TEMPORAL: la protección de estos endpoints se activa en un PR posterior, una vez mergeadas las dos features
                pathPattern(HttpMethod.GET, "/players"), pathPattern(HttpMethod.GET, "/players/**"));
    }

    /**
     * Se permite el dispatch {@code ERROR} para que Boot pueda renderizar los errores: sin eso, un
     * 400 o un 404 en una ruta pública sale disfrazado de 401. Pedir {@code /error} directamente
     * sigue exigiendo credencial, porque no es una ruta pública.
     */
    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http, RequestMatcher publicEndpoints) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(publicEndpoints).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler));
        return http.build();
    }

    /**
     * La consola de H2 solo existe en {@code local}. Necesita frames del mismo origen y no maneja
     * el token de CSRF.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Profile("local")
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }
}
