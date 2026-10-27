package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.FixtureRowDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.MatchCentreDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.MatchEventDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.MatchPlayerDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.MatchSideDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.QualifierDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.TournamentFixturesDto;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto.TournamentMatchDto;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.CardKind;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedCard;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedGoal;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Side;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.ReportedFixture;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.ReportedTeam;

/**
 * Traduce el vocabulario de WhoScored al modelo y no cuenta nada (research D7, D8 y D10): suma los
 * mapas minuto → valor de cada métrica, elige el rating del último minuto y traduce los
 * calificadores de los goles y los códigos de las tarjetas. Quién entró, los minutos jugados, los
 * goles, las asistencias y las tarjetas de cada jugador los decide el modelo.
 */
@Component
public class WhoScoredMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhoScoredMapper.class);

    private static final int FINISHED_STATUS_CODE = 6;
    private static final int CARD_EVENT_TYPE = 17;
    private static final int YELLOW_CARD_TYPE = 31;
    private static final int SECOND_YELLOW_CARD_TYPE = 32;
    private static final int RED_CARD_TYPE = 33;
    private static final String OWN_GOAL_QUALIFIER = "OwnGoal";
    private static final String ASSISTED_QUALIFIER = "Assisted";
    private static final String RATINGS = "ratings";
    private static final int RATING_SCALE = 2;
    private static final int NO_MINUTE = 0;
    private static final Set<String> FINISHED_FIXTURE_STATUSES = Set.of("FT", "AET", "PEN");
    private static final DateTimeFormatter FIXTURE_DATE = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final Pattern MINUTE = Pattern.compile("\\d{1,3}");

    /**
     * La clave de {@code stats} de cada métrica que sale de la planilla (research D8). Los quites son
     * {@code tackleSuccessful} y no {@code tacklesTotal}, que también cuenta las veces que el jugador
     * fue gambeteado; las faltas vienen mal escritas en la fuente.
     */
    private static final Map<Metric, String> SOURCE_KEYS = sourceKeys();

    /**
     * Los equipos del torneo de la liga en la página de la liga, sin repetir y en orden de aparición.
     */
    public List<ReportedTeam> toTeams(TournamentFixturesDto tournamentFixtures, League league) {
        int tournamentId = WhoScoredTournament.of(league).tournamentId();
        Map<String, ReportedTeam> teams = new LinkedHashMap<>();
        listOf(tournamentFixtures.tournaments()).stream()
                .filter(tournament -> Objects.equals(tournament.tournamentId(), tournamentId))
                .flatMap(tournament -> listOf(tournament.matches()).stream())
                .flatMap(WhoScoredMapper::teamsOf)
                .forEach(team -> teams.putIfAbsent(team.whoscoredId(), team));
        return List.copyOf(teams.values());
    }

    /**
     * Las filas del torneo de la liga. Las copas y los torneos europeos se descartan; las de la
     * temporada anterior las descarta el modelo, por fecha (FR-003).
     */
    public List<ReportedFixture> toFixtures(List<FixtureRowDto> rows, League league) {
        int tournamentId = WhoScoredTournament.of(league).tournamentId();
        return rows.stream()
                .filter(row -> Objects.equals(row.tournamentId(), tournamentId))
                .flatMap(row -> toFixture(row).stream())
                .toList();
    }

    public ReportedMatchStats toMatchStats(String matchWhoscoredId, MatchCentreDto matchCentre) {
        List<ReportedAppearance> appearances = Stream.concat(
                        appearancesOf(matchCentre.home(), Side.HOME),
                        appearancesOf(matchCentre.away(), Side.AWAY))
                .toList();
        List<MatchEventDto> events = listOf(matchCentre.events());
        List<ReportedGoal> goals = events.stream()
                .filter(event -> Boolean.TRUE.equals(event.isGoal()))
                .map(WhoScoredMapper::toGoal)
                .toList();
        List<ReportedCard> cards = events.stream()
                .filter(WhoScoredMapper::isCard)
                .flatMap(event -> toCard(matchWhoscoredId, event))
                .toList();
        return new ReportedMatchStats(
                matchWhoscoredId,
                Objects.equals(matchCentre.statusCode(), FINISHED_STATUS_CODE),
                idOf(teamId(matchCentre.home())),
                idOf(teamId(matchCentre.away())),
                Objects.requireNonNullElse(matchCentre.expandedMaxMinute(), NO_MINUTE),
                appearances,
                goals,
                cards);
    }

    private static Stream<ReportedTeam> teamsOf(TournamentMatchDto match) {
        return Stream.of(
                new ReportedTeam(idOf(match.homeTeamId()), match.homeTeamName()),
                new ReportedTeam(idOf(match.awayTeamId()), match.awayTeamName()));
    }

    /**
     * Una fila con una fecha que no se puede leer se descarta: sin fecha no se puede alinear con un
     * calendario.
     */
    private static Optional<ReportedFixture> toFixture(FixtureRowDto row) {
        Optional<LocalDate> date = dateOf(row);
        boolean finished = row.status() != null && FINISHED_FIXTURE_STATUSES.contains(row.status());
        return date.map(day -> new ReportedFixture(idOf(row.id()), day, idOf(row.homeTeamId()), row.homeTeamName(),
                idOf(row.awayTeamId()), row.awayTeamName(), finished));
    }

    private static Optional<LocalDate> dateOf(FixtureRowDto row) {
        try {
            return Optional.of(LocalDate.parse(Objects.requireNonNullElse(row.date(), ""), FIXTURE_DATE));
        } catch (DateTimeParseException e) {
            LOGGER.warn("Se descarta la fila {} de WhoScored: la fecha '{}' no se puede leer ({}).", row.id(),
                    row.date(), e.getMessage());
            return Optional.empty();
        }
    }

    private static Stream<ReportedAppearance> appearancesOf(MatchSideDto side, Side whichSide) {
        if (side == null) {
            return Stream.empty();
        }
        return listOf(side.players()).stream().map(player -> toAppearance(player, whichSide));
    }

    private static ReportedAppearance toAppearance(MatchPlayerDto player, Side side) {
        Map<String, Map<String, BigDecimal>> stats = Objects.requireNonNullElse(player.stats(), Map.of());
        return new ReportedAppearance(
                idOf(player.playerId()),
                player.name(),
                player.age(),
                side,
                Boolean.TRUE.equals(player.isFirstEleven()),
                player.subbedInExpandedMinute(),
                player.subbedOutExpandedMinute(),
                sourceCounts(stats),
                rating(stats.get(RATINGS)),
                Boolean.TRUE.equals(player.isManOfTheMatch()));
    }

    /**
     * Cada métrica es la suma de su mapa minuto → valor. Una métrica que falta suma 0.
     */
    private static MetricCounts sourceCounts(Map<String, Map<String, BigDecimal>> stats) {
        Map<Metric, Integer> counts = new EnumMap<>(Metric.class);
        SOURCE_KEYS.forEach((metric, key) -> counts.put(metric, sum(stats.get(key))));
        return MetricCounts.of(counts);
    }

    private static int sum(Map<String, BigDecimal> valuesByMinute) {
        if (valuesByMinute == null) {
            return 0;
        }
        return valuesByMinute.values().stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .intValue();
    }

    /**
     * El valor del mayor minuto numérico, como lo muestra la tabla del partido: "10" va después de
     * "9". Se redondea a dos decimales. Sin {@code ratings}, no hay rating.
     */
    private static BigDecimal rating(Map<String, BigDecimal> ratingsByMinute) {
        if (ratingsByMinute == null) {
            return null;
        }
        int lastMinute = Integer.MIN_VALUE;
        BigDecimal last = null;
        for (Map.Entry<String, BigDecimal> rating : ratingsByMinute.entrySet()) {
            String key = rating.getKey();
            if (isMinute(key) && rating.getValue() != null && Integer.parseInt(key) >= lastMinute) {
                lastMinute = Integer.parseInt(key);
                last = rating.getValue();
            }
        }
        return last == null ? null : last.setScale(RATING_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean isMinute(String key) {
        return key != null && MINUTE.matcher(key).matches();
    }

    /**
     * Un gol con {@code isOwnGoal} o el calificador {@code OwnGoal} es en contra. Solo un gol con
     * {@code Assisted} tiene asistidor (research D10).
     */
    private static ReportedGoal toGoal(MatchEventDto event) {
        boolean ownGoal = Boolean.TRUE.equals(event.isOwnGoal()) || hasQualifier(event, OWN_GOAL_QUALIFIER);
        String assister = hasQualifier(event, ASSISTED_QUALIFIER) ? idOf(event.relatedPlayerId()) : null;
        return new ReportedGoal(idOf(event.playerId()), ownGoal, assister);
    }

    private static boolean isCard(MatchEventDto event) {
        return event.type() != null && Objects.equals(event.type().value(), CARD_EVENT_TYPE);
    }

    /**
     * Una tarjeta con un código desconocido se ignora y queda un WARN.
     */
    private static Stream<ReportedCard> toCard(String matchWhoscoredId, MatchEventDto event) {
        Integer cardType = event.cardType() == null ? null : event.cardType().value();
        Optional<CardKind> kind = cardKind(cardType);
        if (kind.isEmpty()) {
            LOGGER.warn("Se ignora una tarjeta de tipo desconocido ({}) en el partido {} de WhoScored.", cardType,
                    matchWhoscoredId);
            return Stream.empty();
        }
        int minute = Objects.requireNonNullElse(event.expandedMinute(), NO_MINUTE);
        return Stream.of(new ReportedCard(idOf(event.playerId()), kind.get(), minute));
    }

    private static Optional<CardKind> cardKind(Integer cardType) {
        if (cardType == null) {
            return Optional.empty();
        }
        return switch (cardType) {
            case YELLOW_CARD_TYPE -> Optional.of(CardKind.YELLOW);
            case SECOND_YELLOW_CARD_TYPE -> Optional.of(CardKind.SECOND_YELLOW);
            case RED_CARD_TYPE -> Optional.of(CardKind.RED);
            default -> Optional.empty();
        };
    }

    private static boolean hasQualifier(MatchEventDto event, String displayName) {
        return listOf(event.qualifiers()).stream()
                .map(QualifierDto::type)
                .anyMatch(type -> type != null && displayName.equals(type.displayName()));
    }

    private static Long teamId(MatchSideDto side) {
        return side == null ? null : side.teamId();
    }

    /**
     * Los ids de WhoScored son números en el JSON y texto en el modelo, como los de Football-Data.
     */
    private static String idOf(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private static <T> List<T> listOf(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static Map<Metric, String> sourceKeys() {
        Map<Metric, String> keys = new EnumMap<>(Metric.class);
        keys.put(Metric.SHOTS, "shotsTotal");
        keys.put(Metric.SHOTS_ON_TARGET, "shotsOnTarget");
        keys.put(Metric.KEY_PASSES, "passesKey");
        keys.put(Metric.PASSES, "passesTotal");
        keys.put(Metric.ACCURATE_PASSES, "passesAccurate");
        keys.put(Metric.SUCCESSFUL_DRIBBLES, "dribblesWon");
        keys.put(Metric.TACKLES, "tackleSuccessful");
        keys.put(Metric.INTERCEPTIONS, "interceptions");
        keys.put(Metric.CLEARANCES, "clearances");
        keys.put(Metric.AERIALS_WON, "aerialsWon");
        keys.put(Metric.FOULS_COMMITTED, "foulsCommited");
        return Collections.unmodifiableMap(keys);
    }
}
