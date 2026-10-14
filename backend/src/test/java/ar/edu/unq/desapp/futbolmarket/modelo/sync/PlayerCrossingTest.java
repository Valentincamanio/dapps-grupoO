package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.ARSENAL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON_MAN_UTD_STATS;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.LEON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.NDIAYE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.NDIAYE_APPEARANCE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.OBRIEN;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.PICKFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.TOTTENHAM;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.WISSA;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.ageOf;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossedSeasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.evertonManUtd;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.matchBetween;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.players;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Side;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

class PlayerCrossingTest {

    private static final int DAVIES_AGE = 33;
    private static final int DANILO_AGE = 27;

    private final CatalogPlayers catalog = CatalogPlayers.of(players());
    private final Match evertonManUtd = evertonManUtd();
    private final Match tottenhamArsenal = matchBetween(crossedSeasonMatches(), TOTTENHAM, ARSENAL);

    @Test
    void cruzaPorIdDeWhoScoredSinMirarElNombre() {
        PlayerCrossing crossing = new PlayerCrossing(catalog, evertonManUtd, EVERTON_MAN_UTD_STATS);

        Optional<Player> crossed = crossing.cross(appearance("93145", "J. Pickford", 32, Side.HOME));

        assertThat(crossed).contains(PICKFORD);
        assertThat(crossing.newlyCrossed()).isEmpty();
    }

    @Test
    void cruzaPorNombreEntreLosJugadoresDelEquipoDeSuLado() {
        PlayerCrossing crossing = new PlayerCrossing(catalog, evertonManUtd, EVERTON_MAN_UTD_STATS);

        Optional<Player> crossed = crossing.cross(NDIAYE_APPEARANCE);

        assertThat(crossed).contains(NDIAYE.crossWith("393485"));
        assertThat(crossing.newlyCrossed()).containsExactly(NDIAYE.crossWith("393485"));
    }

    @Test
    void entreHomonimosDelEquipoDesempataLaEdadDelDiaDelPartido() {
        PlayerCrossing crossing = new PlayerCrossing(catalog, evertonManUtd, EVERTON_MAN_UTD_STATS);

        Optional<Player> crossed = crossing.cross(appearance("500001", "Diego León", ageOf(LEON), Side.AWAY));

        assertThat(crossed).contains(LEON.crossWith("500001"));
    }

    @Test
    void unJugadorSinFechaDeNacimientoNoGanaUnDesempate() {
        PlayerCrossing crossing = new PlayerCrossing(catalog, evertonManUtd, EVERTON_MAN_UTD_STATS);

        Optional<Player> crossed = crossing.cross(appearance("500002", "Jake O'Brien", ageOf(OBRIEN), Side.HOME));

        assertThat(crossed).contains(OBRIEN.crossWith("500002"));
    }

    @Test
    void homonimosDeLaMismaEdadEnElEquipoQuedanAmbiguos() {
        PlayerCrossing crossing = new PlayerCrossing(catalog, tottenhamArsenal, tottenhamArsenalStats());

        Optional<Player> crossed = crossing.cross(appearance("500003", "Ben Davies", DAVIES_AGE, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason).containsExactly(CrossFailure.AMBIGUOUS);
    }

    @Test
    void elTransferidoSeEncuentraEnTodoElCatalogoPorNombreYEdad() {
        PlayerCrossing crossing = new PlayerCrossing(catalog, tottenhamArsenal, tottenhamArsenalStats());

        Optional<Player> crossed = crossing.cross(appearance("500004", "Yoane Wissa", ageOf(WISSA), Side.HOME));

        assertThat(crossed).contains(WISSA.crossWith("500004"));
    }

    @Test
    void dosHomonimosDeLaMismaEdadFueraDelEquipoQuedanAmbiguos() {
        PlayerCrossing crossing = new PlayerCrossing(catalog, evertonManUtd, EVERTON_MAN_UTD_STATS);

        Optional<Player> crossed = crossing.cross(appearance("500005", "Danilo", DANILO_AGE, Side.AWAY));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason).containsExactly(CrossFailure.AMBIGUOUS);
    }

    @Test
    void unJugadorConOtroIdDeWhoScoredNuncaEsCandidato() {
        PlayerCrossing crossing = new PlayerCrossing(catalog, evertonManUtd, EVERTON_MAN_UTD_STATS);

        Optional<Player> crossed = crossing.cross(appearance("500006", "Jordan Pickford", 32, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason).containsExactly(CrossFailure.NOT_FOUND);
    }

    @Test
    void unJugadorNoSeEligeDosVecesEnElMismoPartido() {
        PlayerCrossing crossing = new PlayerCrossing(catalog, evertonManUtd, EVERTON_MAN_UTD_STATS);

        Optional<Player> first = crossing.cross(NDIAYE_APPEARANCE);
        Optional<Player> second = crossing.cross(appearance("500007", NDIAYE.name(), ageOf(NDIAYE), Side.HOME));

        assertThat(first).contains(NDIAYE.crossWith("393485"));
        assertThat(second).isEmpty();
        assertThat(crossing.newlyCrossed()).containsExactly(NDIAYE.crossWith("393485"));
    }

    @Test
    void losNoCruzadosSalenConSuNombreSuEquipoYElPartido() {
        PlayerCrossing crossing = new PlayerCrossing(catalog, evertonManUtd, EVERTON_MAN_UTD_STATS);

        crossing.cross(appearance("400000", "Kobbie Mainoo", 21, Side.AWAY));

        assertThat(crossing.uncrossed()).containsExactly(new UncrossedPlayer("400000", "Kobbie Mainoo",
                "Manchester United FC", evertonManUtd.externalId(), CrossFailure.NOT_FOUND));
    }

    private static ReportedAppearance appearance(String whoscoredId, String name, Integer age, Side side) {
        return new ReportedAppearance(whoscoredId, name, age, side, true, null, null, MetricCounts.empty(), null,
                false);
    }

    private static ReportedMatchStats tottenhamArsenalStats() {
        return new ReportedMatchStats("1983605", true, "30", "13", StatsFixtures.END_MINUTE, List.of(), List.of(),
                List.of());
    }
}
