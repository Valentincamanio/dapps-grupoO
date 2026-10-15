package ar.edu.unq.desapp.futbolmarket.persistence.mapper.sync;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotMatchEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotPlayerRow;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotStatsEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotTeamEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSnapshot;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduce el snapshot a las líneas del formato de contracts/snapshot-format.md y de vuelta.
 *
 * <p>Usa un {@code JsonMapper} propio y no el de Boot: así el archivo no cambia si cambia la
 * configuración de la aplicación (FR-030). Las claves van en orden fijo y sin espacios, las filas
 * de jugadores son arrays con las 21 columnas del encabezado, y el rating lleva siempre dos
 * decimales.</p>
 */
@Component
public class StatsSnapshotLineMapper {
    private static final String FORMAT = "futbolmarket-whoscored-snapshot";
    private static final int VERSION = 1;
    private static final int RATING_SCALE = 2;
    private static final int TRUE = 1;
    private static final int FALSE = 0;
    private static final List<String> PLAYER_COLUMNS = playerColumns();
    private static final String ROW_FORMAT_PROBLEM = "Una fila de jugador del snapshot no tiene el formato esperado.";
    private static final int PLAYER_COLUMN = 0;
    private static final int WHOSCORED_COLUMN = 1;
    private static final int STARTER_COLUMN = 2;
    private static final int MINUTES_COLUMN = 3;
    private static final int FIRST_METRIC_COLUMN = 4;
    private static final int RATING_COLUMN = FIRST_METRIC_COLUMN + Metric.values().length;
    private static final int MAN_OF_THE_MATCH_COLUMN = RATING_COLUMN + 1;

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();
    private final ObjectWriter entryWriter = jsonMapper.writerFor(EntryLine.class);
    private final String header = jsonMapper.writeValueAsString(new HeaderLine(FORMAT, VERSION, PLAYER_COLUMNS));

    /**
     * El encabezado exacto y las entradas en orden canónico.
     */
    public List<String> toLines(StatsSnapshot snapshot) {
        Stream<EntryLine> entries = Stream.of(
                        snapshot.teams().stream().map(StatsSnapshotLineMapper::toLine),
                        snapshot.matches().stream().map(StatsSnapshotLineMapper::toLine),
                        snapshot.stats().stream().map(StatsSnapshotLineMapper::toLine))
                .flatMap(Function.identity());
        return Stream.concat(Stream.of(header), entries.map(entryWriter::writeValueAsString)).toList();
    }

    /**
     * La carga exige el encabezado exacto: otro formato, otra versión u otras columnas no lo son.
     */
    public boolean isHeader(String line) {
        return header.equals(line);
    }

    /**
     * Una línea que no se puede leer propaga la {@code JacksonException}; una fila de jugador con
     * otro formato o una clave repetida, la {@code CatalogInvariantException}.
     */
    public StatsSnapshot fromLines(List<String> entries) {
        List<SnapshotTeamEntry> teams = new ArrayList<>();
        List<SnapshotMatchEntry> matches = new ArrayList<>();
        List<SnapshotStatsEntry> stats = new ArrayList<>();
        for (String line : entries) {
            switch (jsonMapper.readValue(line, EntryLine.class)) {
                case TeamLine team -> teams.add(new SnapshotTeamEntry(team.league(), team.team(), team.whoscored()));
                case MatchLine match ->
                        matches.add(new SnapshotMatchEntry(match.league(), match.match(), match.whoscored()));
                case StatsLine statsLine -> stats.add(new SnapshotStatsEntry(statsLine.league(), statsLine.match(),
                        statsLine.players().stream().map(StatsSnapshotLineMapper::toRow).toList()));
            }
        }
        return new StatsSnapshot(teams, matches, stats);
    }

    private static TeamLine toLine(SnapshotTeamEntry entry) {
        return new TeamLine(entry.league(), entry.teamExternalId(), entry.whoscoredId());
    }

    private static MatchLine toLine(SnapshotMatchEntry entry) {
        return new MatchLine(entry.league(), entry.matchExternalId(), entry.whoscoredId());
    }

    private static StatsLine toLine(SnapshotStatsEntry entry) {
        return new StatsLine(entry.league(), entry.matchExternalId(),
                entry.players().stream().map(StatsSnapshotLineMapper::toColumns).toList());
    }

    /**
     * Las 21 columnas del encabezado, en ese orden.
     */
    private static List<Object> toColumns(SnapshotPlayerRow row) {
        List<Object> columns = new ArrayList<>(PLAYER_COLUMNS.size());
        columns.add(row.playerExternalId());
        columns.add(row.whoscoredId());
        columns.add(flag(row.starter()));
        columns.add(row.minutesPlayed());
        Arrays.stream(Metric.values()).forEach(metric -> columns.add(row.counts().get(metric)));
        columns.add(row.rating() == null ? null : row.rating().setScale(RATING_SCALE, RoundingMode.HALF_UP));
        columns.add(flag(row.manOfTheMatch()));
        return columns;
    }

    private static SnapshotPlayerRow toRow(List<Object> columns) {
        if (columns == null || columns.size() != PLAYER_COLUMNS.size()) {
            throw new CatalogInvariantException(ROW_FORMAT_PROBLEM);
        }
        Map<Metric, Integer> counts = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            counts.put(metric, number(columns.get(FIRST_METRIC_COLUMN + metric.ordinal())));
        }
        return new SnapshotPlayerRow(
                text(columns.get(PLAYER_COLUMN), false),
                text(columns.get(WHOSCORED_COLUMN), true),
                number(columns.get(STARTER_COLUMN)) != FALSE,
                number(columns.get(MINUTES_COLUMN)),
                MetricCounts.of(counts),
                rating(columns.get(RATING_COLUMN)),
                number(columns.get(MAN_OF_THE_MATCH_COLUMN)) != FALSE);
    }

    private static String text(Object value, boolean nullable) {
        if (value instanceof String text) {
            return text;
        }
        if (value == null && nullable) {
            return null;
        }
        throw new CatalogInvariantException(ROW_FORMAT_PROBLEM);
    }

    private static int number(Object value) {
        if (value instanceof Integer number) {
            return number;
        }
        throw new CatalogInvariantException(ROW_FORMAT_PROBLEM);
    }

    private static BigDecimal rating(Object value) {
        return switch (value) {
            case null -> null;
            case BigDecimal decimal -> decimal;
            case Integer integer -> BigDecimal.valueOf(integer);
            default -> throw new CatalogInvariantException(ROW_FORMAT_PROBLEM);
        };
    }

    private static int flag(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * Las columnas de una fila: los ids, titular y minutos, las 15 métricas en el orden de
     * {@link Metric} con su nombre en camelCase, el rating y la figura.
     */
    private static List<String> playerColumns() {
        List<String> columns = new ArrayList<>(List.of("player", "whoscored", "starter", "minutesPlayed"));
        Arrays.stream(Metric.values()).map(StatsSnapshotLineMapper::camelCase).forEach(columns::add);
        columns.addAll(List.of("rating", "manOfTheMatch"));
        return List.copyOf(columns);
    }

    private static String camelCase(Metric metric) {
        String[] words = metric.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder(words[0]);
        for (int index = 1; index < words.length; index++) {
            name.append(Character.toUpperCase(words[index].charAt(0))).append(words[index].substring(1));
        }
        return name.toString();
    }

    @JsonPropertyOrder({"format", "version", "playerColumns"})
    record HeaderLine(String format, int version, List<String> playerColumns) {
    }

    /**
     * Una entrada del snapshot. El {@code type} va primero y elige el tipo de línea.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TeamLine.class, name = "team"),
            @JsonSubTypes.Type(value = MatchLine.class, name = "match"),
            @JsonSubTypes.Type(value = StatsLine.class, name = "stats")
    })
    sealed interface EntryLine permits TeamLine, MatchLine, StatsLine {
    }

    @JsonPropertyOrder({"league", "team", "whoscored"})
    record TeamLine(@JsonProperty(required = true) League league,
                    @JsonProperty(required = true) String team,
                    @JsonProperty(required = true) String whoscored) implements EntryLine {
    }

    @JsonPropertyOrder({"league", "match", "whoscored"})
    record MatchLine(@JsonProperty(required = true) League league,
                     @JsonProperty(required = true) String match,
                     @JsonProperty(required = true) String whoscored) implements EntryLine {
    }

    @JsonPropertyOrder({"league", "match", "players"})
    record StatsLine(@JsonProperty(required = true) League league,
                     @JsonProperty(required = true) String match,
                     @JsonProperty(required = true) List<List<Object>> players) implements EntryLine {
    }
}
