package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.ARSENAL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.CASEMIRO;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.PICKFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.TOTTENHAM;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossed;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossedSeasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.evertonManUtd;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.matchBetween;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.matchEntry;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.row;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.statsEntry;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.teamEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

class StatsSnapshotTest {

    @Test
    void ordenaPorLigaYDentroDeCadaLigaPorElLargoYDespuesAlfabeticamente() {
        List<SnapshotTeamEntry> teams = List.of(
                new SnapshotTeamEntry(League.BUNDESLIGA, "5", "37"),
                new SnapshotTeamEntry(League.PREMIER, "62", "31"),
                new SnapshotTeamEntry(League.PREMIER, "10", "40"),
                new SnapshotTeamEntry(League.PREMIER, "9", "41"));
        List<SnapshotMatchEntry> matches = List.of(
                new SnapshotMatchEntry(League.LIGUE_1, "100", "1"),
                new SnapshotMatchEntry(League.PREMIER, "99", "2"));

        StatsSnapshot snapshot = new StatsSnapshot(teams, matches, List.of());

        assertThat(snapshot.teams())
                .extracting(SnapshotTeamEntry::league, SnapshotTeamEntry::teamExternalId)
                .containsExactly(tuple(League.PREMIER, "9"), tuple(League.PREMIER, "10"),
                        tuple(League.PREMIER, "62"), tuple(League.BUNDESLIGA, "5"));
        assertThat(snapshot.matches()).extracting(SnapshotMatchEntry::matchExternalId).containsExactly("99", "100");
    }

    @Test
    void ordenaLasFilasDeJugadoresPorElIdDeFootballDataDelJugador() {
        SnapshotStatsEntry entry = new SnapshotStatsEntry(League.PREMIER, "560603", List.of(
                rowFor("100"), rowFor("9"), rowFor("10")));

        StatsSnapshot snapshot = new StatsSnapshot(List.of(), List.of(), List.of(entry));

        assertThat(snapshot.stats().getFirst().players())
                .extracting(SnapshotPlayerRow::playerExternalId)
                .containsExactly("9", "10", "100");
    }

    @Test
    void rechazaDosEntradasDelMismoTipoConElMismoId() {
        List<SnapshotTeamEntry> teams = List.of(
                new SnapshotTeamEntry(League.PREMIER, "62", "31"),
                new SnapshotTeamEntry(League.LA_LIGA, "62", "77"));

        assertThatThrownBy(() -> new StatsSnapshot(teams, List.of(), List.of()))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El snapshot tiene dos entradas team con el id 62.");
    }

    @Test
    void unPartidoPuedeTenerUnaEntradaMatchYOtraStats() {
        StatsSnapshot snapshot = snapshot();

        Set<String> matchIds = snapshot.matchExternalIds();

        assertThat(snapshot.matches()).hasSize(2);
        assertThat(snapshot.stats()).hasSize(1);
        assertThat(matchIds).containsExactly("560603", "560621");
    }

    @Test
    void ofArmaLasEntradasDeEquiposPartidosYEstadisticasDesdeLaBase() {
        Match evertonManUtd = evertonManUtd().markStatsLoaded();
        Match tottenhamArsenal = matchBetween(crossedSeasonMatches(), TOTTENHAM, ARSENAL).markStatsLoaded();
        List<PlayerMatchStats> stats = List.of(
                new PlayerMatchStats(1L, PICKFORD, evertonManUtd, true, 96, MetricCounts.empty(),
                        new BigDecimal("7.10"), false),
                new PlayerMatchStats(2L, CASEMIRO, evertonManUtd, true, 62, MetricCounts.empty(), null, false));

        StatsSnapshot snapshot = StatsSnapshot.of(List.of(crossed(MAN_UTD), crossed(EVERTON)), List.of(evertonManUtd),
                List.of(evertonManUtd, tottenhamArsenal), stats);

        assertThat(snapshot.teams()).containsExactly(
                new SnapshotTeamEntry(League.PREMIER, "62", "31"),
                new SnapshotTeamEntry(League.PREMIER, "66", "32"));
        assertThat(snapshot.matches()).containsExactly(new SnapshotMatchEntry(League.PREMIER, "560603", "1983603"));
        assertThat(snapshot.stats())
                .extracting(SnapshotStatsEntry::matchExternalId, entry -> entry.players().size())
                .containsExactly(tuple("560603", 2), tuple(tottenhamArsenal.externalId(), 0));
        assertThat(snapshot.stats().getFirst().players())
                .extracting(SnapshotPlayerRow::playerExternalId, SnapshotPlayerRow::whoscoredId,
                        SnapshotPlayerRow::minutesPlayed)
                .containsExactly(tuple("3204", "93145", 96), tuple("3255", null, 62));
    }

    @Test
    void mergeConservaLasEntradasVigentesYSumaSoloLasDeLaBaseQueNoEstan() {
        StatsSnapshot current = snapshot();
        StatsSnapshot fromDatabase = new StatsSnapshot(
                List.of(new SnapshotTeamEntry(League.PREMIER, EVERTON.externalId(), "77"), teamEntry(TOTTENHAM)),
                List.of(matchEntry(EVERTON, MAN_UTD), matchEntry(TOTTENHAM, ARSENAL)),
                List.of(statsEntry(EVERTON, MAN_UTD, row(PICKFORD, "93145", 90))));

        StatsSnapshot merged = current.merge(fromDatabase);

        assertThat(merged.teams()).containsExactly(teamEntry(EVERTON), teamEntry(MAN_UTD), teamEntry(TOTTENHAM));
        assertThat(merged.matches()).containsExactlyInAnyOrder(matchEntry(EVERTON, MAN_UTD),
                matchEntry(MAN_UTD, EVERTON), matchEntry(TOTTENHAM, ARSENAL));
        assertThat(merged.stats()).containsExactlyElementsOf(current.stats());
    }

    @Test
    void forLeagueDejaSoloLasEntradasDeEsaLiga() {
        StatsSnapshot snapshot = new StatsSnapshot(
                List.of(teamEntry(EVERTON), new SnapshotTeamEntry(League.BUNDESLIGA, "5", "37")),
                List.of(new SnapshotMatchEntry(League.BUNDESLIGA, "540001", "1900001")),
                snapshot().stats());

        StatsSnapshot premier = snapshot.forLeague(League.PREMIER);

        assertThat(premier.teams()).containsExactly(teamEntry(EVERTON));
        assertThat(premier.matches()).isEmpty();
        assertThat(premier.stats()).containsExactlyElementsOf(snapshot().stats());
    }

    @Test
    void elSnapshotVacioNoTieneEntradas() {
        StatsSnapshot empty = StatsSnapshot.empty();

        assertThat(empty.teams()).isEmpty();
        assertThat(empty.matches()).isEmpty();
        assertThat(empty.stats()).isEmpty();
        assertThat(empty.teamExternalIds()).isEmpty();
    }

    private static SnapshotPlayerRow rowFor(String playerExternalId) {
        return new SnapshotPlayerRow(playerExternalId, null, true, 90, MetricCounts.empty(), null, false);
    }
}
