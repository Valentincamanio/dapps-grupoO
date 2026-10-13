package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.team.TeamSnapshot;

/**
 * Decide en qué equipo queda un jugador que la fuente informa en más de un plantel (FR-015 y
 * research D9). Las demás apariciones no se escriben y van al informe.
 *
 * <p>La regla: un jugador guardado conserva su equipo actual si es uno de los informados y, si no,
 * queda en el primero según el orden de procesamiento. Uno nuevo queda en la primera aparición con
 * nombre y posición, para no saltearlo si alguna lo trae completo, o en la primera si ninguna está
 * completa. El orden es el de las ligas recibidas y, dentro de cada una, el de la fuente.</p>
 *
 * <p>Es inmutable: {@link #of} decide como si todos los jugadores fueran nuevos, y
 * {@link #resolve} devuelve otra asignación que tiene en cuenta a los guardados.</p>
 */
public final class SquadAssignment {
    private final Map<String, List<Appearance>> duplicatedAppearances;
    private final Map<String, Appearance> keptAppearances;

    private SquadAssignment(Map<String, List<Appearance>> duplicatedAppearances,
                            Map<String, Appearance> keptAppearances) {
        this.duplicatedAppearances = duplicatedAppearances;
        this.keptAppearances = keptAppearances;
    }

    public static SquadAssignment of(List<LeagueSnapshot> snapshots) {
        Map<String, List<Appearance>> appearancesByPlayer = new LinkedHashMap<>();
        snapshots.stream()
                .flatMap(snapshot -> snapshot.teams().stream())
                .flatMap(team -> team.squad().stream().map(player -> new Appearance(player, team)))
                .forEach(appearance -> addIfInAnotherTeam(appearancesByPlayer, appearance));
        Map<String, List<Appearance>> duplicated = new LinkedHashMap<>();
        appearancesByPlayer.forEach((externalId, appearances) -> {
            if (appearances.size() > 1) {
                duplicated.put(externalId, List.copyOf(appearances));
            }
        });
        return new SquadAssignment(duplicated, chooseKeptForAll(duplicated, Map.of()));
    }

    public List<String> duplicatedPlayerExternalIds() {
        return List.copyOf(duplicatedAppearances.keySet());
    }

    /**
     * Vuelve a decidir los duplicados con los jugadores guardados, que solo hacen falta para
     * conocer su equipo actual.
     */
    public SquadAssignment resolve(List<Player> currentPlayers) {
        Map<String, Player> currentByExternalId = currentPlayers.stream()
                .collect(Collectors.toMap(Player::externalId, Function.identity(), (first, second) -> first));
        return new SquadAssignment(duplicatedAppearances, chooseKeptForAll(duplicatedAppearances, currentByExternalId));
    }

    /**
     * Indica si esa aparición se escribe: el jugador no está duplicado, o ese es su equipo elegido.
     */
    public boolean keeps(String playerExternalId, String teamExternalId) {
        Appearance kept = keptAppearances.get(playerExternalId);
        return kept == null || kept.teamExternalId().equals(teamExternalId);
    }

    public List<DuplicatedPlayer> duplicates() {
        List<DuplicatedPlayer> duplicates = new ArrayList<>();
        duplicatedAppearances.forEach((externalId, appearances) -> {
            Appearance kept = keptAppearances.get(externalId);
            appearances.stream()
                    .filter(appearance -> !appearance.teamExternalId().equals(kept.teamExternalId()))
                    .map(ignored -> new DuplicatedPlayer(externalId, playerName(kept, ignored), kept.team().name(),
                            ignored.team().name()))
                    .forEach(duplicates::add);
        });
        return List.copyOf(duplicates);
    }

    /**
     * El mismo jugador dos veces en el mismo equipo no es un duplicado: solo cuenta la primera.
     */
    private static void addIfInAnotherTeam(Map<String, List<Appearance>> appearancesByPlayer, Appearance appearance) {
        List<Appearance> appearances =
                appearancesByPlayer.computeIfAbsent(appearance.playerExternalId(), externalId -> new ArrayList<>());
        if (appearances.stream().noneMatch(other -> other.teamExternalId().equals(appearance.teamExternalId()))) {
            appearances.add(appearance);
        }
    }

    private static Map<String, Appearance> chooseKeptForAll(Map<String, List<Appearance>> duplicated,
                                                            Map<String, Player> currentByExternalId) {
        Map<String, Appearance> kept = new LinkedHashMap<>();
        duplicated.forEach((externalId, appearances) ->
                kept.put(externalId, chooseKept(appearances, currentByExternalId.get(externalId))));
        return kept;
    }

    private static Appearance chooseKept(List<Appearance> appearances, Player current) {
        if (current != null) {
            String currentTeam = current.team().externalId();
            return appearances.stream()
                    .filter(appearance -> appearance.teamExternalId().equals(currentTeam))
                    .findFirst()
                    .orElse(appearances.getFirst());
        }
        return appearances.stream()
                .filter(appearance -> appearance.player().isComplete())
                .findFirst()
                .orElse(appearances.getFirst());
    }

    /**
     * El nombre puede faltar en la aparición elegida; en ese caso se informa el de la ignorada.
     */
    private static String playerName(Appearance kept, Appearance ignored) {
        return kept.player().name() != null ? kept.player().name() : ignored.player().name();
    }

    private record Appearance(PlayerSnapshot player, TeamSnapshot team) {
        String playerExternalId() {
            return player.externalId();
        }

        String teamExternalId() {
            return team.externalId();
        }
    }
}
