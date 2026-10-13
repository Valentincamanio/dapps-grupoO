package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.team.TeamSnapshot;

/**
 * Las reglas de la escritura de una liga. El servicio la usa paso a paso dentro de la transacción
 * de la liga: le pasa lo que hay guardado, recibe lo que tiene que guardar y, al final, le pide el
 * resultado con los conteos y las listas del informe.
 *
 * <p>Es mutable porque acumula el resultado mientras se escribe, y vive solo durante la escritura
 * de una liga.</p>
 */
public class LeagueSync {
    private final LeagueSnapshot snapshot;
    private final SquadAssignment assignment;
    private final Tally teams = new Tally();
    private final Tally players = new Tally();
    private final Tally matches = new Tally();
    private final List<SkippedPlayer> skippedPlayers = new ArrayList<>();
    private final List<SkippedMatch> skippedMatches = new ArrayList<>();
    private final List<Player> reactivatedPlayers = new ArrayList<>();
    private Season savedSeason;

    public LeagueSync(LeagueSnapshot snapshot, SquadAssignment assignment) {
        this.snapshot = snapshot;
        this.assignment = assignment;
    }

    /**
     * Recibe los equipos guardados cuyo {@code externalId} está en el snapshot.
     */
    public List<Team> teamsToSave(List<Team> existing) {
        Map<String, Team> existingByExternalId = byExternalId(existing, Team::externalId);
        List<Team> toSave = new ArrayList<>();
        for (TeamSnapshot reported : snapshot.teams()) {
            Team current = existingByExternalId.get(reported.externalId());
            if (current == null) {
                toSave.add(reported.toNewTeam(snapshot.league()));
                teams.countCreated();
            } else {
                toSave.add(current.updateFrom(reported, snapshot.league()));
                teams.countUpdated();
            }
        }
        return toSave;
    }

    /**
     * Recibe los jugadores guardados del snapshot, con su equipo, y los equipos de la liga ya
     * guardados, con su id.
     */
    public List<Player> playersToSave(List<Player> existing, List<Team> savedTeams) {
        Map<String, Player> existingByExternalId = byExternalId(existing, Player::externalId);
        Map<String, Team> teamsByExternalId = byExternalId(savedTeams, Team::externalId);
        Set<String> processed = new HashSet<>();
        List<Player> toSave = new ArrayList<>();
        for (TeamSnapshot reportedTeam : snapshot.teams()) {
            for (PlayerSnapshot reported : reportedTeam.squad()) {
                if (isWritten(reported, reportedTeam, processed)) {
                    playerToSave(reported, reportedTeam, existingByExternalId.get(reported.externalId()),
                            teamsByExternalId.get(reportedTeam.externalId()))
                            .ifPresent(toSave::add);
                }
            }
        }
        return toSave;
    }

    public Season seasonToSave(Optional<Season> existing) {
        return existing.map(current -> current.updateFrom(snapshot.season())).orElse(snapshot.season());
    }

    /**
     * Recibe los partidos guardados del snapshot, la temporada ya guardada y los equipos del catálogo
     * que referencian los partidos, que pueden no ser de la liga.
     */
    public List<Match> matchesToSave(List<Match> existing, Season savedSeason, List<Team> catalogTeams) {
        this.savedSeason = savedSeason;
        Map<String, Match> existingByExternalId = byExternalId(existing, Match::externalId);
        Map<String, Team> teamsByExternalId = byExternalId(catalogTeams, Team::externalId);
        List<Match> toSave = new ArrayList<>();
        for (MatchSnapshot reported : snapshot.matches()) {
            matchToSave(reported, existingByExternalId.get(reported.externalId()), teamsByExternalId)
                    .ifPresent(toSave::add);
        }
        return toSave;
    }

    public LeagueSyncResult result() {
        return new LeagueSyncResult(snapshot.league(), LeagueSyncStatus.SUCCEEDED, null, savedSeason,
                teams.toCounts(), players.toCounts(), matches.toCounts(), skippedPlayers, skippedMatches,
                reactivatedPlayers);
    }

    /**
     * Una aparición que {@link SquadAssignment} no conserva ya está en el informe como duplicado, y
     * la repetición de un jugador en el mismo equipo se escribe una sola vez.
     */
    private boolean isWritten(PlayerSnapshot reported, TeamSnapshot reportedTeam, Set<String> processed) {
        return assignment.keeps(reported.externalId(), reportedTeam.externalId())
                && processed.add(reported.externalId());
    }

    private Optional<Player> playerToSave(PlayerSnapshot reported, TeamSnapshot reportedTeam, Player current,
                                          Team team) {
        if (current != null) {
            Player updated = current.updateFrom(reported, team);
            players.countUpdated();
            if (!current.active()) {
                reactivatedPlayers.add(updated);
            }
            return Optional.of(updated);
        }
        if (reported.isComplete()) {
            players.countCreated();
            return Optional.of(reported.toNewPlayer(team));
        }
        players.countSkipped();
        skippedPlayers.add(new SkippedPlayer(reported.externalId(), reported.name(), reportedTeam.name(),
                reported.missingDataReason()));
        return Optional.empty();
    }

    /**
     * Un estado desconocido se revisa antes que los equipos: los dos omiten el partido sin hacer
     * fallar la liga (FR-024).
     */
    private Optional<Match> matchToSave(MatchSnapshot reported, Match current, Map<String, Team> teamsByExternalId) {
        if (reported.status() == null) {
            return skip(reported, MatchSkipReason.UNKNOWN_STATUS);
        }
        Team home = teamsByExternalId.get(reported.homeTeamExternalId());
        Team away = teamsByExternalId.get(reported.awayTeamExternalId());
        if (home == null || away == null) {
            return skip(reported, MatchSkipReason.UNKNOWN_TEAM);
        }
        if (current != null) {
            matches.countUpdated();
            return Optional.of(current.updateFrom(reported, home, away));
        }
        matches.countCreated();
        return Optional.of(reported.toNewMatch(savedSeason, home, away));
    }

    private Optional<Match> skip(MatchSnapshot reported, MatchSkipReason reason) {
        matches.countSkipped();
        skippedMatches.add(new SkippedMatch(reported.externalId(), reported.homeTeamExternalId(),
                reported.awayTeamExternalId(), reported.utcDate(), reason));
        return Optional.empty();
    }

    private static <T> Map<String, T> byExternalId(List<T> items, Function<T, String> externalId) {
        return items.stream().collect(Collectors.toMap(externalId, Function.identity(), (first, second) -> first));
    }

    private static final class Tally {
        private int created;
        private int updated;
        private int skipped;

        void countCreated() {
            created++;
        }

        void countUpdated() {
            updated++;
        }

        void countSkipped() {
            skipped++;
        }

        EntityCounts toCounts() {
            return new EntityCounts(created, updated, skipped);
        }
    }
}
