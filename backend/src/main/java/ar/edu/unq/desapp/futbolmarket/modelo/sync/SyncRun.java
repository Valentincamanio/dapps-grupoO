package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * La sincronización en curso. Es mutable porque acumula los resultados mientras corre; no la
 * comparten dos hilos, porque hay a lo sumo una sincronización a la vez (research D11).
 *
 * <p>Decide si se puede inactivar y a quién: solo una completa con las cinco ligas en éxito sabe
 * quién dejó las ligas (FR-017 y FR-018).</p>
 */
public class SyncRun {
    private final SyncType type;
    private final SyncOrigin origin;
    private final Instant startedAt;
    private final List<League> leagues;
    private final Set<String> seenPlayerExternalIds = new HashSet<>();
    private final Map<League, LeagueSyncResult> results = new EnumMap<>(League.class);
    private SquadAssignment squadAssignment = SquadAssignment.of(List.of());

    private SyncRun(SyncType type, SyncOrigin origin, Instant startedAt, List<League> leagues) {
        this.type = type;
        this.origin = origin;
        this.startedAt = startedAt;
        this.leagues = leagues;
    }

    public static SyncRun full(SyncOrigin origin, Instant startedAt) {
        return new SyncRun(SyncType.FULL, origin, startedAt, List.of(League.values()));
    }

    /**
     * Solo el disparo manual puede pedir una sola liga.
     */
    public static SyncRun singleLeague(League league, Instant startedAt) {
        return new SyncRun(SyncType.SINGLE_LEAGUE, SyncOrigin.MANUAL, startedAt, List.of(league));
    }

    public SyncType type() {
        return type;
    }

    public SyncOrigin origin() {
        return origin;
    }

    public List<League> leagues() {
        return leagues;
    }

    /**
     * Todos los jugadores de los planteles cuentan como vistos, aunque después se omitan, estén
     * duplicados o lleguen sin posición: siguen en las ligas.
     */
    public void registerSnapshots(List<LeagueSnapshot> snapshots) {
        squadAssignment = SquadAssignment.of(snapshots);
        snapshots.forEach(snapshot -> seenPlayerExternalIds.addAll(snapshot.playerExternalIds()));
    }

    public List<String> duplicatedPlayerExternalIds() {
        return squadAssignment.duplicatedPlayerExternalIds();
    }

    public void resolveDuplicates(List<Player> currentPlayers) {
        squadAssignment = squadAssignment.resolve(currentPlayers);
    }

    public SquadAssignment squadAssignment() {
        return squadAssignment;
    }

    public void recordSuccess(LeagueSyncResult result) {
        results.put(result.league(), result);
    }

    /**
     * Una liga que se descargó bien pero no se pudo escribir también queda fallida.
     */
    public void recordFailure(League league, String reason) {
        results.put(league, LeagueSyncResult.failed(league, reason));
    }

    public boolean canDeactivate() {
        return type == SyncType.FULL && leagues.stream().allMatch(this::succeeded);
    }

    public List<Player> playersToDeactivate(List<Player> activePlayers) {
        if (!canDeactivate()) {
            return List.of();
        }
        return activePlayers.stream()
                .filter(player -> !seenPlayerExternalIds.contains(player.externalId()))
                .toList();
    }

    /**
     * Arma el informe con las ligas en el orden del enum, aunque los resultados se hayan
     * registrado en otro orden.
     */
    public SyncReport finish(Instant finishedAt, List<Player> inactivated) {
        return new SyncReport(type, origin, startedAt, finishedAt, List.copyOf(results.values()),
                squadAssignment.duplicates(), inactivated, canDeactivate());
    }

    private boolean succeeded(League league) {
        LeagueSyncResult result = results.get(league);
        return result != null && result.succeeded();
    }
}
