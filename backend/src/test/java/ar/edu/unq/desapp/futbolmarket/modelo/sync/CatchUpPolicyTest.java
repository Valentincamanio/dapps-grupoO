package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

/**
 * La tabla de la puesta al día (research D22; HU3, escenarios 2 a 5 y 9; FR-034 y FR-035).
 */
class CatchUpPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-12T07:00:00Z");
    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    private final CatchUpPolicy policy = new CatchUpPolicy(SEVEN_DAYS);

    /**
     * Una celda vacía es una fuente sin ninguna completa registrada, como una base de la 005
     * (escenario 9).
     */
    @ParameterizedTest(name = "Football-Data de {0} días y WhoScored de {1} días: {2}")
    @CsvSource({
            ",  , CHAIN",
            ",  9, CHAIN",
            ",  2, CHAIN",
            "8, , CHAIN",
            "8, 9, CHAIN",
            "8, 2, CHAIN",
            "7, , WHOSCORED_ONLY",
            "7, 9, WHOSCORED_ONLY",
            "7, 2, NONE",
            "3, , WHOSCORED_ONLY",
            "3, 9, WHOSCORED_ONLY",
            "3, 2, NONE"
    })
    void decideSegunLaAntiguedadDeLaUltimaCompletaDeCadaFuente(Integer footballDataDays, Integer whoScoredDays,
                                                               CatchUp expected) {
        Optional<LastFullSync> footballData = lastFullSync(SyncSource.FOOTBALL_DATA, footballDataDays);
        Optional<LastFullSync> whoScored = lastFullSync(SyncSource.WHOSCORED, whoScoredDays);

        CatchUp decision = policy.decide(footballData, whoScored, NOW);

        assertThat(decision).isEqualTo(expected);
    }

    @Test
    void unaCompletaDeSieteDiasYUnSegundoEstaVencida() {
        Optional<LastFullSync> footballData = Optional.of(new LastFullSync(SyncSource.FOOTBALL_DATA,
                NOW.minus(SEVEN_DAYS).minusSeconds(1)));
        Optional<LastFullSync> whoScored = lastFullSync(SyncSource.WHOSCORED, 2);

        CatchUp decision = policy.decide(footballData, whoScored, NOW);

        assertThat(decision).isEqualTo(CatchUp.CHAIN);
    }

    @Test
    void unaAntiguedadMaximaCeroSeRechaza() {
        assertThatThrownBy(() -> new CatchUpPolicy(Duration.ZERO))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("La antigüedad máxima de la puesta al día tiene que ser positiva.");
    }

    @Test
    void unaAntiguedadMaximaNegativaSeRechaza() {
        assertThatThrownBy(() -> new CatchUpPolicy(Duration.ofDays(-1)))
                .isInstanceOf(CatalogInvariantException.class);
    }

    @Test
    void sinAntiguedadMaximaSeRechaza() {
        assertThatThrownBy(() -> new CatchUpPolicy(null))
                .isInstanceOf(CatalogInvariantException.class);
    }

    private static Optional<LastFullSync> lastFullSync(SyncSource source, Integer days) {
        if (days == null) {
            return Optional.empty();
        }
        return Optional.of(new LastFullSync(source, NOW.minus(Duration.ofDays(days))));
    }
}
