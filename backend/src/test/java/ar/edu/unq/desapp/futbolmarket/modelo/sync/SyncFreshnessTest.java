package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link CatchUpPolicy#freshness}: la regla del plazo que usa el indicador de salud {@code sync}
 * (research D8 de la 012).
 */
class SyncFreshnessTest {

    private static final Instant NOW = Instant.parse("2026-11-22T07:00:00Z");
    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    private final CatchUpPolicy policy = new CatchUpPolicy(SEVEN_DAYS);

    @Test
    void sinNingunaCompletaEsNever() {
        SyncFreshness freshness = policy.freshness(Optional.empty(), NOW);

        assertThat(freshness).isEqualTo(SyncFreshness.NEVER);
    }

    @Test
    void unaCompletaDeHaceMenosDelPlazoEstaAlDia() {
        Optional<LastFullSync> last = finishedAgo(Duration.ofDays(2));

        SyncFreshness freshness = policy.freshness(last, NOW);

        assertThat(freshness).isEqualTo(SyncFreshness.UP_TO_DATE);
    }

    @Test
    void unaCompletaDeExactamenteElPlazoEstaAlDia() {
        Optional<LastFullSync> last = finishedAgo(SEVEN_DAYS);

        SyncFreshness freshness = policy.freshness(last, NOW);

        assertThat(freshness).isEqualTo(SyncFreshness.UP_TO_DATE);
    }

    @Test
    void unaCompletaDeUnNanosegundoMasQueElPlazoEstaVencida() {
        Optional<LastFullSync> last = finishedAgo(SEVEN_DAYS.plusNanos(1));

        SyncFreshness freshness = policy.freshness(last, NOW);

        assertThat(freshness).isEqualTo(SyncFreshness.STALE);
    }

    /**
     * Con WhoScored al día, {@code decide} da {@link CatchUp#NONE} solo si Football-Data está al día:
     * así se compara la regla de {@code freshness} con la de la puesta al día.
     */
    @ParameterizedTest(name = "antigüedad de {0} segundos")
    @ValueSource(longs = {0, 86_400, 604_799, 604_800, 604_801, 1_209_600})
    void freshnessCoincideConLoQueDecideConsideraAlDia(long ageSeconds) {
        Optional<LastFullSync> footballData = finishedAgo(Duration.ofSeconds(ageSeconds));
        Optional<LastFullSync> whoScored = Optional.of(new LastFullSync(SyncSource.WHOSCORED, NOW));

        boolean upToDateByFreshness = policy.freshness(footballData, NOW) == SyncFreshness.UP_TO_DATE;
        boolean upToDateByDecide = policy.decide(footballData, whoScored, NOW) == CatchUp.NONE;

        assertThat(upToDateByFreshness).isEqualTo(upToDateByDecide);
    }

    @Test
    void sinNingunaCompletaFreshnessYDecideCoinciden() {
        Optional<LastFullSync> whoScored = Optional.of(new LastFullSync(SyncSource.WHOSCORED, NOW));

        SyncFreshness freshness = policy.freshness(Optional.empty(), NOW);
        CatchUp decision = policy.decide(Optional.empty(), whoScored, NOW);

        assertThat(freshness).isEqualTo(SyncFreshness.NEVER);
        assertThat(decision).isEqualTo(CatchUp.CHAIN);
    }

    private static Optional<LastFullSync> finishedAgo(Duration age) {
        return Optional.of(new LastFullSync(SyncSource.FOOTBALL_DATA, NOW.minus(age)));
    }
}
