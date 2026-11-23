package ar.edu.unq.desapp.futbolmarket.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentación de la API en Swagger UI.
 *
 * <p>No declara un requirement global a propósito: así los endpoints públicos (registro, login y
 * la consulta de jugadores) se muestran sin candado, y cada controller protegido declara los dos
 * esquemas como alternativas (research D16).</p>
 *
 * <p>Swagger UI muestra dos grupos (research D9 de la 012): la API de negocio y el monitoreo de
 * Actuator. Los nombres de grupo van en inglés porque forman parte de la URL
 * ({@code /v3/api-docs/monitoring}); lo que ve el usuario va en español. La especificación por
 * defecto ({@code /v3/api-docs}) sigue existiendo y lo incluye todo.</p>
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String API_KEY_AUTH = "apiKeyAuth";

    public static final String BUSINESS_GROUP = "business";
    public static final String MONITORING_GROUP = "monitoring";

    private static final String BEARER_SCHEME = "bearer";
    private static final String BEARER_FORMAT = "JWT";
    private static final String API_KEY_HEADER = "X-API-Key";

    private static final String BUSINESS_DISPLAY_NAME = "API de negocio";
    private static final String MONITORING_DISPLAY_NAME = "Monitoreo";
    private static final String ALL_PATHS = "/**";
    private static final String ACTUATOR_PATHS = "/actuator/**";
    private static final String PUBLIC_HEALTH_PATH = "/actuator/health";

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

    /** Todo lo que no es Actuator. */
    @Bean
    public GroupedOpenApi businessApi() {
        return GroupedOpenApi.builder()
                .group(BUSINESS_GROUP)
                .displayName(BUSINESS_DISPLAY_NAME)
                .pathsToMatch(ALL_PATHS)
                .pathsToExclude(ACTUATOR_PATHS)
                .build();
    }

    /**
     * Los endpoints de Actuator. Cada operación, salvo la raíz pública de salud, declara los dos
     * esquemas como alternativas, para que Swagger UI mande la credencial al ejecutarla.
     */
    @Bean
    public GroupedOpenApi monitoringApi() {
        return GroupedOpenApi.builder()
                .group(MONITORING_GROUP)
                .displayName(MONITORING_DISPLAY_NAME)
                .pathsToMatch(ACTUATOR_PATHS)
                .addOpenApiCustomizer(protectedActuatorOperations())
                .build();
    }

    private static OpenApiCustomizer protectedActuatorOperations() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                if (!isPublicHealth(path, method)) {
                    requireCredential(operation);
                }
            }));
        };
    }

    private static boolean isPublicHealth(String path, PathItem.HttpMethod method) {
        return PUBLIC_HEALTH_PATH.equals(path) && method == PathItem.HttpMethod.GET;
    }

    /** Dos requirements separados: alcanza con cualquiera de los dos esquemas. */
    private static void requireCredential(Operation operation) {
        operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_AUTH));
    }
}
