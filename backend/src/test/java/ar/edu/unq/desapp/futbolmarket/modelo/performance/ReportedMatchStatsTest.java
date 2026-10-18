package ar.edu.unq.desapp.futbolmarket.modelo.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Un Everton - Manchester United con 2 minutos agregados en el primer tiempo y 4 en el segundo:
 * el segundo tiempo empieza en el minuto expandido 47 (desfasaje de 2) y el partido termina en el
 * 96 (research D9).
 */
class ReportedMatchStatsTest {

    private static final String EVERTON_WHOSCORED_ID = "31";
    private static final String MAN_UTD_WHOSCORED_ID = "32";
    private static final int END_MINUTE = 96;

    private static final ReportedAppearance PICKFORD = starter("101", "Jordan Pickford", Side.HOME, null);
    private static final ReportedAppearance CALVERT_LEWIN = starter("102", "Dominic Calvert-Lewin", Side.HOME, 72);
    private static final ReportedAppearance BETO = substitute("103", "Beto", Side.HOME, 72);
    private static final ReportedAppearance NDIAYE = new ReportedAppearance("104", "Iliman Ndiaye", 26, Side.HOME,
            true, null, null, sourceCounts(), new BigDecimal("7.65"), true);
    private static final ReportedAppearance GREALISH = starter("105", "Jack Grealish", Side.HOME, null);
    private static final ReportedAppearance CASEMIRO = starter("201", "Casemiro", Side.AWAY, null);
    private static final ReportedAppearance MARTINEZ = starter("202", "Lisandro Martínez", Side.AWAY, null);
    private static final ReportedAppearance MAGUIRE = starter("203", "Harry Maguire", Side.AWAY, null);
    private static final ReportedAppearance FERNANDES = starter("204", "Bruno Fernandes", Side.AWAY, null);
    private static final ReportedAppearance BAYINDIR = substitute("205", "Altay Bayindir", Side.AWAY, null);
    private static final ReportedAppearance ZIRKZEE = substitute("206", "Joshua Zirkzee", Side.AWAY, 93);

    private static final ReportedMatchStats REPORTED = new ReportedMatchStats(
            "1983600", true, EVERTON_WHOSCORED_ID, MAN_UTD_WHOSCORED_ID, END_MINUTE,
            List.of(PICKFORD, CALVERT_LEWIN, BETO, NDIAYE, GREALISH, CASEMIRO, MARTINEZ, MAGUIRE, FERNANDES,
                    BAYINDIR, ZIRKZEE),
            List.of(
                    new ReportedGoal("104", false, "105"),
                    new ReportedGoal("203", true, "204")),
            List.of(
                    new ReportedCard("202", CardKind.YELLOW, 30),
                    new ReportedCard("104", CardKind.YELLOW, 40),
                    new ReportedCard("205", CardKind.YELLOW, 50),
                    new ReportedCard("201", CardKind.RED, 62),
                    new ReportedCard("202", CardKind.SECOND_YELLOW, 81)));

    private static final Season SEASON = new Season(
            1L, "2502", League.PREMIER, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), 8);
    private static final Team EVERTON = new Team(1L, "62", "Everton FC", null, League.PREMIER, EVERTON_WHOSCORED_ID);
    private static final Team MAN_UTD =
            new Team(2L, "66", "Manchester United FC", null, League.PREMIER, MAN_UTD_WHOSCORED_ID);
    private static final Team TOTTENHAM = new Team(3L, "73", "Tottenham Hotspur FC", null, League.PREMIER, "30");

    @Test
    void unTitularQueJuegaTodoElPartidoSumaLosMinutosConLoAgregado() {
        int minutes = REPORTED.minutesPlayed(PICKFORD);

        assertThat(minutes).isEqualTo(96);
    }

    @Test
    void elTitularReemplazadoYSuReemplazoSeRepartenElPartido() {
        int replacedMinutes = REPORTED.minutesPlayed(CALVERT_LEWIN);
        int replacementMinutes = REPORTED.minutesPlayed(BETO);

        assertThat(replacedMinutes).isEqualTo(72);
        assertThat(replacementMinutes).isEqualTo(24);
    }

    @Test
    void elExpulsadoSumaLosMinutosHastaLaRojaYUnaRoja() {
        int minutes = REPORTED.minutesPlayed(CASEMIRO);
        int redCards = REPORTED.redCards(CASEMIRO);
        int yellowCards = REPORTED.yellowCards(CASEMIRO);

        assertThat(minutes).isEqualTo(62);
        assertThat(redCards).isEqualTo(1);
        assertThat(yellowCards).isZero();
    }

    @Test
    void laDobleAmarillaEsUnaAmarillaYUnaRojaYCortaLosMinutosEnLaSegunda() {
        int yellowCards = REPORTED.yellowCards(MARTINEZ);
        int redCards = REPORTED.redCards(MARTINEZ);
        int minutes = REPORTED.minutesPlayed(MARTINEZ);

        assertThat(yellowCards).isEqualTo(1);
        assertThat(redCards).isEqualTo(1);
        assertThat(minutes).isEqualTo(81);
    }

    @Test
    void elGolEnContraNoSumaGolASuAutorNiAsistenciaANadie() {
        int ownGoalScorerGoals = REPORTED.goals(MAGUIRE);
        int ownGoalAssisterAssists = REPORTED.assists(FERNANDES);

        assertThat(ownGoalScorerGoals).isZero();
        assertThat(ownGoalAssisterAssists).isZero();
    }

    @Test
    void unGolAsistidoSumaUnGolAlAutorYUnaAsistenciaAlAsistidor() {
        int scorerGoals = REPORTED.goals(NDIAYE);
        int scorerAssists = REPORTED.assists(NDIAYE);
        int assisterGoals = REPORTED.goals(GREALISH);
        int assisterAssists = REPORTED.assists(GREALISH);

        assertThat(scorerGoals).isEqualTo(1);
        assertThat(scorerAssists).isZero();
        assertThat(assisterGoals).isZero();
        assertThat(assisterAssists).isEqualTo(1);
    }

    @Test
    void unSuplenteQueNoEntroNoEstaEnLaCanchaAunqueTengaUnaTarjeta() {
        List<ReportedAppearance> onPitch = REPORTED.onPitch();

        assertThat(onPitch).containsExactly(PICKFORD, CALVERT_LEWIN, BETO, NDIAYE, GREALISH, CASEMIRO, MARTINEZ,
                MAGUIRE, FERNANDES, ZIRKZEE);
        assertThat(BAYINDIR.entered()).isFalse();
    }

    @Test
    void unSuplenteQueEntraEnElAgregadoDelSegundoTiempoSumaLosMinutosHastaElFinal() {
        int minutes = REPORTED.minutesPlayed(ZIRKZEE);

        assertThat(minutes).isEqualTo(3);
    }

    @Test
    void sonLosDatosDelPartidoConLosEquiposCruzadosYLaMismaLocalia() {
        Match match = match(EVERTON, MAN_UTD);

        boolean isFor = REPORTED.isFor(match);

        assertThat(isFor).isTrue();
    }

    @Test
    void noSonLosDatosDelPartidoConLaLocaliaInvertida() {
        Match match = match(MAN_UTD, EVERTON);

        boolean isFor = REPORTED.isFor(match);

        assertThat(isFor).isFalse();
    }

    @Test
    void noSonLosDatosDelPartidoConOtroEquipo() {
        Match match = match(EVERTON, TOTTENHAM);

        boolean isFor = REPORTED.isFor(match);

        assertThat(isFor).isFalse();
    }

    @Test
    void noSonLosDatosDeUnPartidoConUnEquipoSinCruzar() {
        Team uncrossedManUtd = new Team(2L, "66", "Manchester United FC", null, League.PREMIER);
        Match match = match(EVERTON, uncrossedManUtd);

        boolean isFor = REPORTED.isFor(match);

        assertThat(isFor).isFalse();
    }

    @Test
    void toStatsArmaLasQuinceMetricasLosMinutosElRatingYLaFigura() {
        Player player = new Player(1L, "8135", "Iliman Ndiaye", Position.FORWARD, EVERTON);
        Match match = match(EVERTON, MAN_UTD);

        PlayerMatchStats stats = REPORTED.toStats(NDIAYE, player, match);

        assertThat(stats).isEqualTo(new PlayerMatchStats(null, player, match, true, 96, expectedNdiayeCounts(),
                new BigDecimal("7.65"), true));
    }

    private static MetricCounts sourceCounts() {
        return MetricCounts.of(Map.ofEntries(
                Map.entry(Metric.SHOTS, 4),
                Map.entry(Metric.SHOTS_ON_TARGET, 2),
                Map.entry(Metric.KEY_PASSES, 1),
                Map.entry(Metric.PASSES, 30),
                Map.entry(Metric.ACCURATE_PASSES, 25),
                Map.entry(Metric.SUCCESSFUL_DRIBBLES, 3),
                Map.entry(Metric.TACKLES, 1),
                Map.entry(Metric.INTERCEPTIONS, 2),
                Map.entry(Metric.CLEARANCES, 1),
                Map.entry(Metric.AERIALS_WON, 2),
                Map.entry(Metric.FOULS_COMMITTED, 1)));
    }

    private static MetricCounts expectedNdiayeCounts() {
        return MetricCounts.of(Map.ofEntries(
                Map.entry(Metric.GOALS, 1),
                Map.entry(Metric.ASSISTS, 0),
                Map.entry(Metric.SHOTS, 4),
                Map.entry(Metric.SHOTS_ON_TARGET, 2),
                Map.entry(Metric.KEY_PASSES, 1),
                Map.entry(Metric.PASSES, 30),
                Map.entry(Metric.ACCURATE_PASSES, 25),
                Map.entry(Metric.SUCCESSFUL_DRIBBLES, 3),
                Map.entry(Metric.TACKLES, 1),
                Map.entry(Metric.INTERCEPTIONS, 2),
                Map.entry(Metric.CLEARANCES, 1),
                Map.entry(Metric.AERIALS_WON, 2),
                Map.entry(Metric.FOULS_COMMITTED, 1),
                Map.entry(Metric.YELLOW_CARDS, 1),
                Map.entry(Metric.RED_CARDS, 0)));
    }

    private static ReportedAppearance starter(String whoscoredId, String name, Side side, Integer leftAt) {
        return new ReportedAppearance(whoscoredId, name, null, side, true, null, leftAt, MetricCounts.empty(), null,
                false);
    }

    private static ReportedAppearance substitute(String whoscoredId, String name, Side side, Integer enteredAt) {
        return new ReportedAppearance(whoscoredId, name, null, side, false, enteredAt, null, MetricCounts.empty(),
                null, false);
    }

    private static Match match(Team home, Team away) {
        return new Match(1L, "560600", SEASON, Instant.parse("2026-10-17T14:00:00Z"), 8, MatchStatus.FINISHED,
                home, away, null, null, null, "1983600", false);
    }
}
