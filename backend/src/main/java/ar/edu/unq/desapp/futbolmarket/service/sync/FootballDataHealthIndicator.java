package ar.edu.unq.desapp.futbolmarket.service.sync;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import lombok.RequiredArgsConstructor;

/**
 * Componente {@code footballData} de {@code /actuator/health} (FR-040, FR-043; research D8 de la
 * 012): dice solo si hay token de Football-Data configurado. No llama a la API ni muestra el token
 * ni una parte de él. Sin token, {@code UNKNOWN}; nunca {@code DOWN}.
 */
@Component
@RequiredArgsConstructor
public class FootballDataHealthIndicator implements HealthIndicator {

    static final String TOKEN_CONFIGURED = "tokenConfigured";

    private final FootballDataProperties properties;

    @Override
    public Health health() {
        boolean tokenConfigured = properties.hasToken();
        Health.Builder builder = tokenConfigured ? Health.up() : Health.unknown();
        return builder.withDetail(TOKEN_CONFIGURED, tokenConfigured).build();
    }
}
