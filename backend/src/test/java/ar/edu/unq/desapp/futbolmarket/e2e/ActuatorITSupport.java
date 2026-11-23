package ar.edu.unq.desapp.futbolmarket.e2e;

import java.util.UUID;

import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Configuración común de los e2e de Actuator de la 012 ({@code ActuatorContentIT},
 * {@code ActuatorAccessIT} y {@code LoggersEndpointIT}).
 *
 * <p>Las tres clases heredan la misma configuración para compartir <b>un solo</b> contexto de Spring:
 * la caché de contextos de test identifica a un {@link DynamicPropertySource} por el método que lo
 * declara, así que si cada clase tuviera el suyo, cada una levantaría un contexto propio y la suite
 * se quedaría sin memoria. Un {@code ADMIN} dado de alta por propiedades con una H2 propia, como
 * {@code AdminAccountIT}; {@link AutoConfigureMetrics} para que {@code /actuator/prometheus} exista
 * (research D12); y un token de Football-Data ficticio, solo para poder buscarlo en las respuestas.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
@TestPropertySource(properties = "futbolmarket.football-data.token=" + ActuatorITSupport.FAKE_FOOTBALL_DATA_TOKEN)
abstract class ActuatorITSupport {

    static final String FAKE_FOOTBALL_DATA_TOKEN = "tokenFicticioDeFootballData0123456789";

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();

    @DynamicPropertySource
    static void adminConfiguration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:actuator-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1");
    }
}
