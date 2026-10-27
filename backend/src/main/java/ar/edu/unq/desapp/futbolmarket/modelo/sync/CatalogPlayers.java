package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Índice en memoria de todos los jugadores del catálogo, activos o inactivos, con su equipo
 * (research D14). Se carga una vez por corrida: es más barato que una consulta por nombre, y la
 * comparación por nombre normalizado no se puede expresar en JPQL portable.
 *
 * <p>Es mutable y lo usa un solo hilo. Los cruces de un partido entran recién con
 * {@link #confirm}, cuando el partido ya se guardó: si la escritura falla, no queda un cruce que no
 * existe.</p>
 */
public final class CatalogPlayers {
    private final Map<String, Player> byExternalId = new LinkedHashMap<>();
    private final Map<String, String> externalIdByWhoscoredId = new HashMap<>();
    private final Map<NormalizedName, List<String>> externalIdsByName = new HashMap<>();

    private CatalogPlayers() {
    }

    public static CatalogPlayers of(List<Player> players) {
        CatalogPlayers catalog = new CatalogPlayers();
        players.forEach(catalog::index);
        return catalog;
    }

    public Optional<Player> byWhoscoredId(String whoscoredId) {
        return Optional.ofNullable(externalIdByWhoscoredId.get(whoscoredId)).map(byExternalId::get);
    }

    /**
     * Por id de Football-Data, que es como el snapshot ubica a cada jugador.
     */
    public Optional<Player> byExternalId(String externalId) {
        return Optional.ofNullable(byExternalId.get(externalId));
    }

    /**
     * Los jugadores con ese nombre cuyo equipo actual en el catálogo es ese.
     */
    public List<Player> inTeam(Team team, NormalizedName name) {
        return named(name).stream()
                .filter(player -> player.team().externalId().equals(team.externalId()))
                .toList();
    }

    /**
     * Todos los jugadores cuyo equipo actual en el catálogo es ese, activos o inactivos, para los
     * niveles de cruce por nombre (research D14).
     */
    public List<Player> inTeam(Team team) {
        return byExternalId.values().stream()
                .filter(player -> player.team().externalId().equals(team.externalId()))
                .toList();
    }

    public List<Player> named(NormalizedName name) {
        return externalIdsByName.getOrDefault(name, List.of()).stream()
                .map(byExternalId::get)
                .toList();
    }

    /**
     * Todos los jugadores con esa fecha de nacimiento, de cualquier equipo, activos o inactivos,
     * para el cruce de un transferido por la página del jugador (FR-019d). Los que no tienen fecha
     * nunca aparecen.
     */
    public List<Player> bornOn(LocalDate birthDate) {
        return byExternalId.values().stream()
                .filter(player -> birthDate.equals(player.dateOfBirth()))
                .toList();
    }

    /**
     * Toma los jugadores cruzados en un partido ya guardado como su versión actual.
     */
    public void confirm(List<Player> crossed) {
        crossed.forEach(this::index);
    }

    private void index(Player player) {
        Player previous = byExternalId.put(player.externalId(), player);
        if (previous == null) {
            externalIdsByName.computeIfAbsent(NormalizedName.ofPlayer(player.name()), name -> new ArrayList<>())
                    .add(player.externalId());
        }
        if (player.isCrossed()) {
            externalIdByWhoscoredId.put(player.whoscoredId(), player.externalId());
        }
    }
}
