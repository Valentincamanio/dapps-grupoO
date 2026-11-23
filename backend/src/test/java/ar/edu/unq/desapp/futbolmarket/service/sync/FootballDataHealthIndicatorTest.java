package ar.edu.unq.desapp.futbolmarket.service.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;

/**
 * El componente {@code footballData} (contracts/health-indicators.md de la 012; FR-040, FR-051).
 */
class FootballDataHealthIndicatorTest {

    private static final String TOKEN = "tokenSecretoDeFootballData9876543210";

    @Test
    void sinTokenEsUnknownConTokenConfiguredFalse() {
        FootballDataHealthIndicator indicator = new FootballDataHealthIndicator(properties(null));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsExactlyEntriesOf(Map.of("tokenConfigured", false));
    }

    @Test
    void conTokenEnBlancoEsUnknownConTokenConfiguredFalse() {
        FootballDataHealthIndicator indicator = new FootballDataHealthIndicator(properties("   "));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsExactlyEntriesOf(Map.of("tokenConfigured", false));
    }

    @Test
    void conTokenEsUpConTokenConfiguredTrue() {
        FootballDataHealthIndicator indicator = new FootballDataHealthIndicator(properties(TOKEN));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsExactlyEntriesOf(Map.of("tokenConfigured", true));
    }

    @Test
    void losDetallesNuncaContienenElToken() {
        FootballDataHealthIndicator indicator = new FootballDataHealthIndicator(properties(TOKEN));

        Health health = indicator.health();

        assertThat(health.getDetails().toString()).doesNotContain(TOKEN);
        assertThat(health.toString()).doesNotContain(TOKEN);
    }

    private static FootballDataProperties properties(String token) {
        return new FootballDataProperties(token, URI.create("http://football-data.invalid/v4"),
                Duration.ofSeconds(5), Duration.ofSeconds(10),
                new FootballDataProperties.Sync("-", "UTC", false));
    }
}
