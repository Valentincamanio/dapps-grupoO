package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * El contenido del snapshot de estadísticas, en orden canónico (contracts/snapshot-format.md).
 *
 * <p>El orden hace que el archivo sea determinístico (FR-030): dentro de cada tipo, por liga en el
 * orden del enum y, dentro de cada liga, por id de Football-Data, primero por largo y después
 * alfabético, que en ids numéricos es el orden numérico. Las filas de jugadores, por id de
 * Football-Data del jugador, con el mismo criterio. La clave de una entrada es su tipo más su id de
 * Football-Data, y no se puede repetir.</p>
 */
public record StatsSnapshot(List<SnapshotTeamEntry> teams, List<SnapshotMatchEntry> matches,
                            List<SnapshotStatsEntry> stats) {
    private static final Comparator<String> CANONICAL_ID =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());
    private static final String TEAM_TYPE = "team";
    private static final String MATCH_TYPE = "match";
    private static final String STATS_TYPE = "stats";

    public StatsSnapshot {
        teams = canonical(teams, SnapshotTeamEntry::league, SnapshotTeamEntry::teamExternalId, TEAM_TYPE);
        matches = canonical(matches, SnapshotMatchEntry::league, SnapshotMatchEntry::matchExternalId, MATCH_TYPE);
        stats = canonical(withSortedRows(stats), SnapshotStatsEntry::league, SnapshotStatsEntry::matchExternalId,
                STATS_TYPE);
    }

    public static StatsSnapshot empty() {
        return new StatsSnapshot(List.of(), List.of(), List.of());
    }

    /**
     * Las entradas que salen de la base (FR-023 y FR-029): una {@code team} por equipo cruzado, con
     * su liga actual; una {@code match} por partido cruzado, con la liga de su temporada; y una
     * {@code stats} por partido con estadísticas, con una fila por cada estadística guardada.
     */
    public static StatsSnapshot of(List<Team> crossedTeams, List<Match> crossedMatches, List<Match> loadedMatches,
                                   List<PlayerMatchStats> stats) {
        List<SnapshotTeamEntry> teamEntries = crossedTeams.stream()
                .map(team -> new SnapshotTeamEntry(team.league(), team.externalId(), team.whoscoredId()))
                .toList();
        List<SnapshotMatchEntry> matchEntries = crossedMatches.stream()
                .map(match -> new SnapshotMatchEntry(match.season().league(), match.externalId(), match.whoscoredId()))
                .toList();
        Map<String, List<SnapshotPlayerRow>> rowsByMatch = stats.stream()
                .collect(Collectors.groupingBy(playerStats -> playerStats.match().externalId(),
                        Collectors.mapping(StatsSnapshot::row, Collectors.toList())));
        List<SnapshotStatsEntry> statsEntries = loadedMatches.stream()
                .map(match -> new SnapshotStatsEntry(match.season().league(), match.externalId(),
                        rowsByMatch.getOrDefault(match.externalId(), List.of())))
                .toList();
        return new StatsSnapshot(teamEntries, matchEntries, statsEntries);
    }

    /**
     * Combina el snapshot vigente con lo que sale de la base (FR-030 y FR-031): cada entrada vigente
     * se conserva tal cual, también si la base no la tiene o la tiene distinta, y se suman las de la
     * base cuya clave no está.
     */
    public StatsSnapshot merge(StatsSnapshot fromDatabase) {
        return new StatsSnapshot(
                union(teams, fromDatabase.teams(), SnapshotTeamEntry::teamExternalId),
                union(matches, fromDatabase.matches(), SnapshotMatchEntry::matchExternalId),
                union(stats, fromDatabase.stats(), SnapshotStatsEntry::matchExternalId));
    }

    public StatsSnapshot forLeague(League league) {
        return new StatsSnapshot(
                teams.stream().filter(entry -> entry.league() == league).toList(),
                matches.stream().filter(entry -> entry.league() == league).toList(),
                stats.stream().filter(entry -> entry.league() == league).toList());
    }

    /**
     * Los ids de Football-Data de las entradas {@code team}, en orden canónico.
     */
    public Set<String> teamExternalIds() {
        return ids(teams.stream().map(SnapshotTeamEntry::teamExternalId));
    }

    /**
     * Los ids de Football-Data de las entradas {@code match} y {@code stats}.
     */
    public Set<String> matchExternalIds() {
        return ids(Stream.concat(
                matches.stream().map(SnapshotMatchEntry::matchExternalId),
                stats.stream().map(SnapshotStatsEntry::matchExternalId)));
    }

    private static SnapshotPlayerRow row(PlayerMatchStats stats) {
        return new SnapshotPlayerRow(stats.player().externalId(), stats.player().whoscoredId(), stats.starter(),
                stats.minutesPlayed(), stats.counts(), stats.rating(), stats.manOfTheMatch());
    }

    private static <E> List<E> canonical(List<E> entries, Function<E, League> league, Function<E, String> externalId,
                                         String type) {
        List<E> present = entries == null ? List.of() : entries;
        requireUniqueKeys(present, externalId, type);
        return present.stream()
                .sorted(Comparator.comparing(league).thenComparing(externalId, CANONICAL_ID))
                .toList();
    }

    private static <E> void requireUniqueKeys(List<E> entries, Function<E, String> externalId, String type) {
        Set<String> seen = new HashSet<>();
        for (E entry : entries) {
            String id = externalId.apply(entry);
            if (!seen.add(id)) {
                throw new CatalogInvariantException(
                        "El snapshot tiene dos entradas %s con el id %s.".formatted(type, id));
            }
        }
    }

    private static List<SnapshotStatsEntry> withSortedRows(List<SnapshotStatsEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
                .map(entry -> new SnapshotStatsEntry(entry.league(), entry.matchExternalId(), entry.players().stream()
                        .sorted(Comparator.comparing(SnapshotPlayerRow::playerExternalId, CANONICAL_ID))
                        .toList()))
                .toList();
    }

    private static <E> List<E> union(List<E> current, List<E> added, Function<E, String> externalId) {
        Set<String> currentKeys = current.stream().map(externalId).collect(Collectors.toSet());
        Stream<E> newEntries = added.stream().filter(entry -> !currentKeys.contains(externalId.apply(entry)));
        return Stream.concat(current.stream(), newEntries).toList();
    }

    private static Set<String> ids(Stream<String> ids) {
        Set<String> ordered = ids.collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(ordered);
    }
}
