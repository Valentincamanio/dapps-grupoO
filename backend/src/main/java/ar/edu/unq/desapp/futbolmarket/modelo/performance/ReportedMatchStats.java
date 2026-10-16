package ar.edu.unq.desapp.futbolmarket.modelo.performance;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Lo que WhoScored informa de un partido: los dos equipos, la planilla, los goles y las tarjetas,
 * todos por su id de WhoScored.
 *
 * <p>Las reglas del spec para contar viven acá y no en el adapter, que solo traduce el vocabulario
 * de la página (research D7, D9 y D10). Los minutos son los expandidos de WhoScored:
 * {@code endMinute} es el último minuto del partido, con lo agregado en los dos tiempos.</p>
 */
public record ReportedMatchStats(String whoscoredId, boolean finished, String homeTeamWhoscoredId,
                                 String awayTeamWhoscoredId, int endMinute, List<ReportedAppearance> appearances,
                                 List<ReportedGoal> goals, List<ReportedCard> cards) {
    private static final int KICK_OFF_MINUTE = 0;
    private static final int NO_MINUTES = 0;

    public ReportedMatchStats {
        appearances = copy(appearances);
        goals = copy(goals);
        cards = copy(cards);
    }

    /**
     * Desde que entró (el inicio, para un titular) hasta lo primero que pase entre que sale, lo
     * expulsan o termina el partido (FR-005). Un suplente que no entró no jugó minutos.
     */
    public int minutesPlayed(ReportedAppearance appearance) {
        if (!appearance.entered()) {
            return NO_MINUTES;
        }
        int start = appearance.starter() ? KICK_OFF_MINUTE : appearance.enteredAt();
        int end = endMinute;
        if (appearance.leftAt() != null) {
            end = Math.min(end, appearance.leftAt());
        }
        OptionalInt sentOff = sendingOffMinute(appearance);
        if (sentOff.isPresent()) {
            end = Math.min(end, sentOff.getAsInt());
        }
        return Math.max(NO_MINUTES, end - start);
    }

    /**
     * Un gol en contra no suma gol a su autor (FR-006).
     */
    public int goals(ReportedAppearance appearance) {
        return (int) validGoals()
                .filter(goal -> sameId(goal.scorerWhoscoredId(), appearance.whoscoredId()))
                .count();
    }

    /**
     * Un gol en contra no suma asistencia a nadie, aunque traiga asistidor (FR-006).
     */
    public int assists(ReportedAppearance appearance) {
        return (int) validGoals()
                .filter(goal -> sameId(goal.assisterWhoscoredId(), appearance.whoscoredId()))
                .count();
    }

    public int yellowCards(ReportedAppearance appearance) {
        return (int) cardsOf(appearance).filter(card -> card.kind() == CardKind.YELLOW).count();
    }

    /**
     * La segunda amarilla y la roja directa (FR-007).
     */
    public int redCards(ReportedAppearance appearance) {
        return (int) cardsOf(appearance).filter(card -> card.kind().isRed()).count();
    }

    /**
     * Los que entraron a la cancha, en el orden de la fuente. Son los únicos con estadísticas
     * (FR-004).
     */
    public List<ReportedAppearance> onPitch() {
        return appearances.stream().filter(ReportedAppearance::entered).toList();
    }

    /**
     * Si estos datos son los del partido: los dos equipos coinciden con los equipos cruzados del
     * partido, con la misma localía.
     */
    public boolean isFor(Match match) {
        return sameId(homeTeamWhoscoredId, match.homeTeam().whoscoredId())
                && sameId(awayTeamWhoscoredId, match.awayTeam().whoscoredId());
    }

    /**
     * El rendimiento del jugador, sin id: las métricas de la página más los goles, las asistencias
     * y las tarjetas de los eventos, con los minutos, el rating y la figura (FR-002 y FR-008).
     */
    public PlayerMatchStats toStats(ReportedAppearance appearance, Player player, Match match) {
        MetricCounts counts = appearance.sourceCounts()
                .with(Metric.GOALS, goals(appearance))
                .with(Metric.ASSISTS, assists(appearance))
                .with(Metric.YELLOW_CARDS, yellowCards(appearance))
                .with(Metric.RED_CARDS, redCards(appearance));
        return new PlayerMatchStats(null, player, match, appearance.starter(), minutesPlayed(appearance), counts,
                appearance.rating(), appearance.manOfTheMatch());
    }

    private OptionalInt sendingOffMinute(ReportedAppearance appearance) {
        return cardsOf(appearance)
                .filter(card -> card.kind().isRed())
                .mapToInt(ReportedCard::minute)
                .min();
    }

    private Stream<ReportedGoal> validGoals() {
        return goals.stream().filter(goal -> !goal.ownGoal());
    }

    private Stream<ReportedCard> cardsOf(ReportedAppearance appearance) {
        return cards.stream().filter(card -> sameId(card.playerWhoscoredId(), appearance.whoscoredId()));
    }

    /**
     * Un id que falta no coincide con nada, ni siquiera con otro que falta.
     */
    private static boolean sameId(String reported, String expected) {
        return reported != null && reported.equals(expected);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
