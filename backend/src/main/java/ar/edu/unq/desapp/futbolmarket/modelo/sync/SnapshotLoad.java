package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Lo que se carga del snapshot en una liga (FR-024 a FR-026; research D20 y
 * contracts/snapshot-format.md, "Cómo se carga"). No consulta WhoScored ni depende de él (FR-025).
 *
 * <p>Un equipo, un partido o un jugador sin cruzar toma el id del snapshot si está libre. Ante un
 * cruce en conflicto gana la base ({@code CONFLICTING_CROSS}), pero las estadísticas del jugador se
 * cargan igual, porque se ubican por su id de Football-Data. Un id asignado en esta misma carga
 * cuenta como ocupado para las entradas siguientes.</p>
 *
 * <p>Lo que está en el catálogo pero no en la temporada en curso, como un equipo que dejó la liga o
 * un partido de otra temporada, se ignora sin contarlo. Un partido que ya tiene estadísticas también:
 * conserva las suyas.</p>
 */
public final class SnapshotLoad {
    private final Map<String, Team> seasonTeams = new LinkedHashMap<>();
    private final Map<String, Match> seasonMatches = new LinkedHashMap<>();
    private final Set<String> referencedTeams;
    private final Set<String> referencedMatches;
    private final Map<String, String> teamByWhoscoredId = new HashMap<>();
    private final Map<String, String> matchByWhoscoredId = new HashMap<>();
    private final Map<String, String> playerByWhoscoredId = new HashMap<>();
    private final Map<String, Player> crossedPlayers = new HashMap<>();
    private final CatalogPlayers catalog;
    private final List<Team> teamCrosses = new ArrayList<>();
    private final List<Match> matchCrosses = new ArrayList<>();
    private final List<MatchStatsLoad> loads = new ArrayList<>();
    private final Map<SnapshotSkipReason, Integer> skipped = new EnumMap<>(SnapshotSkipReason.class);

    public SnapshotLoad(StatsSnapshot forLeague, List<Match> seasonMatches, List<Team> seasonTeams,
                        CatalogPlayers catalog, CatalogReferences referenced) {
        this.catalog = catalog;
        seasonTeams.forEach(team -> this.seasonTeams.put(team.externalId(), team));
        seasonMatches.forEach(match -> this.seasonMatches.put(match.externalId(), match));
        this.referencedTeams = referenced.teams().stream().map(Team::externalId).collect(Collectors.toSet());
        this.referencedMatches = referenced.matches().stream().map(Match::externalId).collect(Collectors.toSet());
        registerTeamIds(seasonTeams);
        registerTeamIds(referenced.teams());
        registerMatchIds(seasonMatches);
        registerMatchIds(referenced.matches());
        forLeague.teams().forEach(this::applyTeam);
        forLeague.matches().forEach(this::applyMatch);
        forLeague.stats().forEach(this::applyStats);
    }

    /**
     * Los equipos sin cruzar a los que el snapshot les da un id libre.
     */
    public List<Team> teamCrosses() {
        return List.copyOf(teamCrosses);
    }

    /**
     * Los partidos sin cruzar a los que el snapshot les da un id libre, también futuros.
     */
    public List<Match> matchCrosses() {
        return List.copyOf(matchCrosses);
    }

    /**
     * Un {@link MatchStatsLoad} con origen {@code SNAPSHOT} por cada partido pendiente del snapshot.
     */
    public List<MatchStatsLoad> loads() {
        return List.copyOf(loads);
    }

    public Map<SnapshotSkipReason, Integer> skipped() {
        return Collections.unmodifiableMap(new EnumMap<>(skipped));
    }

    private void applyTeam(SnapshotTeamEntry entry) {
        Team team = seasonTeams.get(entry.teamExternalId());
        if (team == null) {
            countIfNotReferenced(referencedTeams, entry.teamExternalId(), SnapshotSkipReason.TEAM_NOT_IN_CATALOG);
            return;
        }
        Outcome outcome = decide(team.whoscoredId(), entry.whoscoredId(), teamByWhoscoredId.get(entry.whoscoredId()));
        if (outcome == Outcome.CROSS) {
            Team crossed = team.crossWith(entry.whoscoredId());
            seasonTeams.put(crossed.externalId(), crossed);
            teamByWhoscoredId.put(entry.whoscoredId(), crossed.externalId());
            teamCrosses.add(crossed);
        }
        countIfConflict(outcome);
    }

    private void applyMatch(SnapshotMatchEntry entry) {
        Match match = seasonMatches.get(entry.matchExternalId());
        if (match == null) {
            countIfNotReferenced(referencedMatches, entry.matchExternalId(), SnapshotSkipReason.MATCH_NOT_IN_CATALOG);
            return;
        }
        Outcome outcome =
                decide(match.whoscoredId(), entry.whoscoredId(), matchByWhoscoredId.get(entry.whoscoredId()));
        if (outcome == Outcome.CROSS) {
            Match crossed = match.crossWith(entry.whoscoredId());
            seasonMatches.put(crossed.externalId(), crossed);
            matchByWhoscoredId.put(entry.whoscoredId(), crossed.externalId());
            matchCrosses.add(crossed);
        }
        countIfConflict(outcome);
    }

    /**
     * Solo los partidos pendientes se cargan. Un jugador que no está en el catálogo se saltea y el
     * resto del partido se carga igual (FR-026).
     */
    private void applyStats(SnapshotStatsEntry entry) {
        Match match = seasonMatches.get(entry.matchExternalId());
        if (match == null) {
            countIfNotReferenced(referencedMatches, entry.matchExternalId(), SnapshotSkipReason.MATCH_NOT_IN_CATALOG);
            return;
        }
        if (match.statsLoaded()) {
            return;
        }
        if (!match.needsStats()) {
            count(SnapshotSkipReason.MATCH_NOT_FINISHED);
            return;
        }
        Match loaded = match.markStatsLoaded();
        List<Player> crossedHere = new ArrayList<>();
        List<PlayerMatchStats> stats = new ArrayList<>();
        for (SnapshotPlayerRow row : entry.players()) {
            Optional<Player> player = currentPlayer(row.playerExternalId());
            if (player.isEmpty()) {
                count(SnapshotSkipReason.PLAYER_NOT_IN_CATALOG);
                continue;
            }
            Player resolved = applyPlayerCross(player.get(), row.whoscoredId(), crossedHere);
            stats.add(new PlayerMatchStats(null, resolved, loaded, row.starter(), row.minutesPlayed(), row.counts(),
                    row.rating(), row.manOfTheMatch()));
        }
        seasonMatches.put(loaded.externalId(), loaded);
        loads.add(new MatchStatsLoad(loaded, crossedHere, stats, LoadSource.SNAPSHOT));
    }

    /**
     * Una fila sin id de WhoScored no cruza a nadie.
     */
    private Player applyPlayerCross(Player player, String whoscoredId, List<Player> crossedHere) {
        Outcome outcome = decide(player.whoscoredId(), whoscoredId, playerOwning(whoscoredId));
        countIfConflict(outcome);
        if (outcome != Outcome.CROSS) {
            return player;
        }
        Player crossed = player.crossWith(whoscoredId);
        crossedPlayers.put(crossed.externalId(), crossed);
        playerByWhoscoredId.put(whoscoredId, crossed.externalId());
        crossedHere.add(crossed);
        return crossed;
    }

    private Optional<Player> currentPlayer(String externalId) {
        Player crossed = crossedPlayers.get(externalId);
        return crossed != null ? Optional.of(crossed) : catalog.byExternalId(externalId);
    }

    private String playerOwning(String whoscoredId) {
        if (whoscoredId == null) {
            return null;
        }
        String assignedHere = playerByWhoscoredId.get(whoscoredId);
        return assignedHere != null
                ? assignedHere
                : catalog.byWhoscoredId(whoscoredId).map(Player::externalId).orElse(null);
    }

    private void registerTeamIds(List<Team> teams) {
        teams.stream()
                .filter(Team::isCrossed)
                .forEach(team -> teamByWhoscoredId.put(team.whoscoredId(), team.externalId()));
    }

    private void registerMatchIds(List<Match> matches) {
        matches.stream()
                .filter(Match::isCrossed)
                .forEach(match -> matchByWhoscoredId.put(match.whoscoredId(), match.externalId()));
    }

    private void countIfNotReferenced(Set<String> referenced, String externalId, SnapshotSkipReason reason) {
        if (!referenced.contains(externalId)) {
            count(reason);
        }
    }

    private void countIfConflict(Outcome outcome) {
        if (outcome == Outcome.CONFLICT) {
            count(SnapshotSkipReason.CONFLICTING_CROSS);
        }
    }

    private void count(SnapshotSkipReason reason) {
        skipped.merge(reason, 1, Integer::sum);
    }

    /**
     * Sin cruzar y con el id libre, se cruza; con el mismo id, nada; con otro id, o con el id de otro,
     * gana la base.
     */
    private static Outcome decide(String currentWhoscoredId, String snapshotWhoscoredId, String owner) {
        if (snapshotWhoscoredId == null) {
            return Outcome.NOTHING;
        }
        if (currentWhoscoredId != null) {
            return currentWhoscoredId.equals(snapshotWhoscoredId) ? Outcome.NOTHING : Outcome.CONFLICT;
        }
        return owner == null ? Outcome.CROSS : Outcome.CONFLICT;
    }

    private enum Outcome {
        NOTHING,
        CROSS,
        CONFLICT
    }
}
