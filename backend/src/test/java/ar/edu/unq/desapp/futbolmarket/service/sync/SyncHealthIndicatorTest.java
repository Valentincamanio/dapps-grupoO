package ar.edu.unq.desapp.futbolmarket.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import ar.edu.unq.desapp.futbolmarket.config.SyncProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.LastFullSyncRepository;

/**
 * El componente {@code sync} (contracts/health-indicators.md de la 012; FR-041, FR-042).
 */
class SyncHealthIndicatorTest {

    private static final Instant NOW = Instant.parse("2026-11-22T07:00:00Z");
    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    private final LastFullSyncRepository repository = mock(LastFullSyncRepository.class);
    private final SyncHealthIndicator indicator = new SyncHealthIndicator(repository,
            new SyncProperties(SEVEN_DAYS), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void lasDosSinSincronizarDanUnknownConSynchronizedFalse() {
        givenLast(SyncSource.FOOTBALL_DATA, null);
        givenLast(SyncSource.WHOSCORED, null);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(source(health, "football_data")).containsExactlyEntriesOf(Map.of("synchronized", false));
        assertThat(source(health, "whoscored")).containsExactlyEntriesOf(Map.of("synchronized", false));
    }

    @Test
    void lasDosAlDiaDanUpConStaleFalseYLaFecha() {
        Instant footballDataEnd = NOW.minus(Duration.ofDays(5));
        Instant whoScoredEnd = NOW.minus(Duration.ofDays(1));
        givenLast(SyncSource.FOOTBALL_DATA, footballDataEnd);
        givenLast(SyncSource.WHOSCORED, whoScoredEnd);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(source(health, "football_data")).containsEntry("synchronized", true)
                .containsEntry("lastSuccessfulFullSync", footballDataEnd.toString())
                .containsEntry("stale", false);
        assertThat(source(health, "whoscored")).containsEntry("synchronized", true)
                .containsEntry("lastSuccessfulFullSync", whoScoredEnd.toString())
                .containsEntry("stale", false);
    }

    @Test
    void unaVencidaDaUnknownConStaleTrueEnEsa() {
        givenLast(SyncSource.FOOTBALL_DATA, NOW.minus(Duration.ofDays(21)));
        givenLast(SyncSource.WHOSCORED, NOW.minus(Duration.ofDays(1)));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(source(health, "football_data")).containsEntry("stale", true);
        assertThat(source(health, "whoscored")).containsEntry("stale", false);
    }

    @Test
    void conExactamenteElPlazoEstaAlDia() {
        givenLast(SyncSource.FOOTBALL_DATA, NOW.minus(SEVEN_DAYS));
        givenLast(SyncSource.WHOSCORED, NOW.minus(SEVEN_DAYS));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(source(health, "football_data")).containsEntry("stale", false);
    }

    @Test
    void conUnNanosegundoMasQueElPlazoEstaVencida() {
        givenLast(SyncSource.FOOTBALL_DATA, NOW.minus(SEVEN_DAYS));
        givenLast(SyncSource.WHOSCORED, NOW.minus(SEVEN_DAYS.plusNanos(1)));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(source(health, "whoscored")).containsEntry("stale", true);
    }

    @Test
    void publicaElPlazoComoDuracionIso8601() {
        givenLast(SyncSource.FOOTBALL_DATA, null);
        givenLast(SyncSource.WHOSCORED, null);

        Health health = indicator.health();

        assertThat(health.getDetails()).containsEntry("catchUpAfter", "PT168H");
    }

    @Test
    void nuncaDaDownEnNingunaCombinacion() {
        Instant[] ends = {null, NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(30))};
        for (Instant footballDataEnd : ends) {
            for (Instant whoScoredEnd : ends) {
                givenLast(SyncSource.FOOTBALL_DATA, footballDataEnd);
                givenLast(SyncSource.WHOSCORED, whoScoredEnd);

                Health health = indicator.health();

                assertThat(health.getStatus()).isIn(Status.UP, Status.UNKNOWN);
            }
        }
    }

    private void givenLast(SyncSource source, Instant finishedAt) {
        Optional<LastFullSync> last = Optional.ofNullable(finishedAt).map(end -> new LastFullSync(source, end));
        when(repository.find(source)).thenReturn(last);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> source(Health health, String key) {
        return (Map<String, Object>) health.getDetails().get(key);
    }
}
