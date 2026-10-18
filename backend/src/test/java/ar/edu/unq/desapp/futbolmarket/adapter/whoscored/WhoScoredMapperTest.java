package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.CodeDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.MatchCentreDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.MatchEventDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.MatchSideDto;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.CardKind;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedCard;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedGoal;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Side;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.ReportedFixture;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.ReportedTeam;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee las fixtures con los extractores y prueba la traducción, sin HTTP. Las reglas de negocio
 * (goles, asistencias, tarjetas por jugador) se aplican con el modelo sobre lo que traduce el
 * mapper, como en la sincronización.
 */
@ExtendWith(OutputCaptureExtension.class)
class WhoScoredMapperTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final WhoScoredMapper mapper = new WhoScoredMapper();

    @Test
    void traduceElPartidoTerminadoConSusEquiposYSuMinutoFinal() throws IOException {
        MatchCentreDto matchCentre = matchCentre("match-1983600.html");

        ReportedMatchStats stats = mapper.toMatchStats("1983600", matchCentre);

        assertThat(stats.whoscoredId()).isEqualTo("1983600");
        assertThat(stats.finished()).isTrue();
        assertThat(stats.homeTeamWhoscoredId()).isEqualTo("19");
        assertThat(stats.awayTeamWhoscoredId()).isEqualTo("23");
        assertThat(stats.endMinute()).isEqualTo(97);
        assertThat(stats.appearances()).hasSize(9);
    }

    @Test
    void calvertLewinSumaDosGolesUnaAmarillaElRatingDelUltimoMinutoYEsLaFigura() throws IOException {
        ReportedMatchStats stats = mapper.toMatchStats("1983600", matchCentre("match-1983600.html"));

        ReportedAppearance calvertLewin = appearance(stats, "Dominic Calvert-Lewin");

        assertThat(calvertLewin.starter()).isTrue();
        assertThat(calvertLewin.leftAt()).isEqualTo(80);
        assertThat(calvertLewin.rating()).isEqualTo(new BigDecimal("8.95"));
        assertThat(calvertLewin.manOfTheMatch()).isTrue();
        assertThat(calvertLewin.sourceCounts().get(Metric.SHOTS)).isEqualTo(5);
        assertThat(calvertLewin.sourceCounts().get(Metric.FOULS_COMMITTED)).isEqualTo(1);
        assertThat(stats.goals(calvertLewin)).isEqualTo(2);
        assertThat(stats.yellowCards(calvertLewin)).isEqualTo(1);
        assertThat(stats.minutesPlayed(calvertLewin)).isEqualTo(80);
    }

    @Test
    void justinSumaLosQuitesGanadosYLaAsistenciaDeUnPaseConSoloShotAssist() throws IOException {
        ReportedMatchStats stats = mapper.toMatchStats("1983600", matchCentre("match-1983600.html"));

        ReportedAppearance justin = appearance(stats, "James Justin");

        assertThat(justin.sourceCounts().get(Metric.TACKLES)).isEqualTo(2);
        assertThat(stats.assists(justin)).isEqualTo(1);
        assertThat(stats.minutesPlayed(justin)).isEqualTo(97);
    }

    @Test
    void bardEntraEnElSetentaYCincoYSuRatingSeRedondeaADosDecimales() throws IOException {
        ReportedMatchStats stats = mapper.toMatchStats("1983600", matchCentre("match-1983600.html"));

        ReportedAppearance bard = appearance(stats, "Melvin Bard");

        assertThat(bard.starter()).isFalse();
        assertThat(bard.enteredAt()).isEqualTo(75);
        assertThat(bard.rating()).isEqualTo(new BigDecimal("6.67"));
        assertThat(stats.minutesPlayed(bard)).isEqualTo(22);
    }

    @Test
    void unSuplenteQueNoEntroNoTieneRating() throws IOException {
        ReportedMatchStats stats = mapper.toMatchStats("1983600", matchCentre("match-1983600.html"));

        ReportedAppearance meslier = appearance(stats, "Illan Meslier");

        assertThat(meslier.entered()).isFalse();
        assertThat(meslier.rating()).isNull();
        assertThat(meslier.side()).isEqualTo(Side.HOME);
    }

    @Test
    void elGolEnContraQuedaMarcadoYNoSumaGolASuAutor() throws IOException {
        ReportedMatchStats stats = mapper.toMatchStats("1983600", matchCentre("match-1983600.html"));

        List<ReportedGoal> goals = stats.goals();

        assertThat(goals).contains(new ReportedGoal("474264", true, null));
        assertThat(stats.goals(appearance(stats, "Dan Burn"))).isZero();
        assertThat(goals).hasSize(5);
    }

    @Test
    void unRemateConAssistedQueNoEsGolNoDaAsistencia() throws IOException {
        ReportedMatchStats stats = mapper.toMatchStats("1983600", matchCentre("match-1983600.html"));

        ReportedAppearance gordon = appearance(stats, "Anthony Gordon");

        assertThat(stats.assists(gordon)).isZero();
        assertThat(stats.goals(gordon)).isEqualTo(1);
        assertThat(stats.goals(appearance(stats, "Joe Willock"))).isZero();
    }

    @Test
    void laSegundaAmarillaEsSecondYellowYLaAmarillaAnuladaSigueSiendoAmarilla() throws IOException {
        ReportedMatchStats stats = mapper.toMatchStats("1984103", matchCentre("match-second-yellow.html"));

        List<ReportedCard> cards = stats.cards();

        assertThat(cards).containsExactly(
                new ReportedCard("300001", CardKind.YELLOW, 50),
                new ReportedCard("300001", CardKind.SECOND_YELLOW, 81));
        assertThat(stats.minutesPlayed(appearance(stats, "Timothy Weah"))).isEqualTo(81);
    }

    @Test
    void unGolAsistidoPorUnRemateTieneAsistidorYUnoSinAssistedNo() throws IOException {
        ReportedMatchStats stats = mapper.toMatchStats("1984103", matchCentre("match-second-yellow.html"));

        List<ReportedGoal> goals = stats.goals();

        assertThat(goals).containsExactly(
                new ReportedGoal("320003", false, "310002"),
                new ReportedGoal("400002", false, "400001"),
                new ReportedGoal("400003", false, null));
    }

    @Test
    void unaTarjetaDeTipoDesconocidoSeIgnoraConUnWarn(CapturedOutput output) {
        MatchEventDto unknownCard = new MatchEventDto(1L, 70, 19L, 141469L, new CodeDto(17, "Card"), null, null,
                List.of(), null, new CodeDto(99, "Unknown"));
        MatchCentreDto matchCentre = new MatchCentreDto(6, 95, new MatchSideDto(19L, "Leeds", List.of()),
                new MatchSideDto(23L, "Newcastle", List.of()), List.of(unknownCard));

        ReportedMatchStats stats = mapper.toMatchStats("1983700", matchCentre);

        assertThat(stats.cards()).isEmpty();
        assertThat(output).contains("Se ignora una tarjeta de tipo desconocido (99) en el partido 1983700");
    }

    @Test
    void unPartidoConOtroStatusCodeNoEstaTerminado() {
        MatchCentreDto matchCentre = new MatchCentreDto(1, null, null, null, null);

        ReportedMatchStats stats = mapper.toMatchStats("1983609", matchCentre);

        assertThat(stats.finished()).isFalse();
        assertThat(stats.endMinute()).isZero();
        assertThat(stats.appearances()).isEmpty();
    }

    @Test
    void toFixturesDescartaLaCopaYLeeLaFechaYElEstado() throws IOException {
        var rows = new FixtureMatchesExtractor(jsonMapper).extract(page("fixtures-everton.html")).orElseThrow();

        List<ReportedFixture> fixtures = mapper.toFixtures(rows, League.PREMIER);

        assertThat(fixtures).extracting(ReportedFixture::whoscoredId)
                .containsExactly("1954001", "1983549", "1983560", "1983609");
        assertThat(fixtures.getLast()).isEqualTo(new ReportedFixture("1983609", LocalDate.of(2026, 10, 17), "31",
                "Everton", "15", "Chelsea", false));
        assertThat(fixtures).extracting(ReportedFixture::whoscoredId, ReportedFixture::finished)
                .contains(tuple("1983549", true), tuple("1983560", true));
    }

    @Test
    void toTeamsDevuelveLosEquiposDelTorneoDeLaLigaSinRepetir() throws IOException {
        var tournamentFixtures = new TournamentFixturesExtractor(jsonMapper).extract(page("league-premier.html"))
                .orElseThrow();

        List<ReportedTeam> teams = mapper.toTeams(tournamentFixtures, League.PREMIER);

        assertThat(teams).containsExactly(
                new ReportedTeam("31", "Everton"),
                new ReportedTeam("15", "Chelsea"),
                new ReportedTeam("19", "Leeds"),
                new ReportedTeam("23", "Newcastle"),
                new ReportedTeam("32", "Man Utd"));
    }

    private MatchCentreDto matchCentre(String name) throws IOException {
        return new MatchCentreExtractor(jsonMapper).extract(page(name)).orElseThrow().orElseThrow();
    }

    private static ReportedAppearance appearance(ReportedMatchStats stats, String name) {
        return stats.appearances().stream()
                .filter(appearance -> appearance.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static String page(String name) throws IOException {
        return new ClassPathResource("whoscored/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
