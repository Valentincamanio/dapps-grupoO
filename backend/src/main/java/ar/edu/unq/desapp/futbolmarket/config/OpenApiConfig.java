package ar.edu.unq.desapp.futbolmarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentación de la API en Swagger UI.
 *
 * <p>No declara un requirement global a propósito: así los endpoints públicos (registro, login y
 * la consulta de jugadores) se muestran sin candado, y cada controller protegido declara los dos
 * esquemas como alternativas (research D16).</p>
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String API_KEY_AUTH = "apiKeyAuth";

    private static final String BEARER_SCHEME = "bearer";
    private static final String BEARER_FORMAT = "JWT";
    private static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    public OpenAPI futbolMarketOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("futbol-market")
                        .version("0.0.1-SNAPSHOT")
                        .description("Mercado simulado de valoración de jugadores de fútbol. "
                                + "Los endpoints protegidos aceptan un token de sesión o una clave de API."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme(BEARER_SCHEME)
                                .bearerFormat(BEARER_FORMAT)
                                .description("Token de sesión que devuelve POST /auth/login."))
                        .addSecuritySchemes(API_KEY_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(API_KEY_HEADER)
                                .description("Clave de API que se entrega una única vez al registrarse.")));
    }
}
