package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Side;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Cruza con el catálogo los jugadores de WhoScored que entraron a la cancha en un partido (FR-019 a
 * FR-022; research D14).
 *
 * <p>Primero por id de WhoScored, sin mirar nombres ni equipo. Si no, entre los del equipo del
 * catálogo de ese lado con el mismo nombre normalizado, desempatando por la edad el día del
 * partido. Si en el equipo no hay ninguno, en todo el catálogo por nombre y misma edad, solo si hay
 * uno único. Nunca es candidato un jugador con otro id de WhoScored ni uno ya elegido en el mismo
 * partido. Nunca se crea un jugador.</p>
 *
 * <p>Es mutable y lo usa un solo hilo, un partido por instancia.</p>
 */
public final class PlayerCrossing {
    private final CatalogPlayers catalog;
    private final Match match;
    private final Set<String> chosen = new HashSet<>();
    private final List<Player> newlyCrossed = new ArrayList<>();
    private final List<UncrossedPlayer> uncrossed = new ArrayList<>();

    public PlayerCrossing(CatalogPlayers catalog, Match match, ReportedMatchStats reported) {
        this.catalog = Objects.requireNonNull(catalog);
        this.match = Objects.requireNonNull(match);
        Objects.requireNonNull(reported);
    }

    /**
     * El jugador del catálogo de un jugador que entró a la cancha. Si no se puede cruzar, queda en
     * {@link #uncrossed()} con su motivo.
     */
    public Optional<Player> cross(ReportedAppearance appearance) {
        Optional<Player> known = catalog.byWhoscoredId(appearance.whoscoredId());
        if (known.isPresent()) {
            chosen.add(known.get().externalId());
            return known;
        }
        Resolution resolution = resolve(appearance);
        if (resolution.player() != null) {
            return Optional.of(choose(resolution.player(), appearance));
        }
        uncrossed.add(new UncrossedPlayer(appearance.whoscoredId(), appearance.name(), teamOf(appearance).name(),
                match.externalId(), resolution.failure()));
        return Optional.empty();
    }

    /**
     * Los jugadores cruzados en este partido, con su id de WhoScored.
     */
    public List<Player> newlyCrossed() {
        return List.copyOf(newlyCrossed);
    }

    public List<UncrossedPlayer> uncrossed() {
        return List.copyOf(uncrossed);
    }

    /**
     * En el equipo, con más de un homónimo desempata la edad, y si no la resuelve es ambiguo. Fuera
     * del equipo, la edad es obligatoria: un nombre solo no alcanza.
     */
    private Resolution resolve(ReportedAppearance appearance) {
        NormalizedName name = NormalizedName.ofPlayer(appearance.name());
        List<Player> inTeam = eligible(catalog.inTeam(teamOf(appearance), name));
        if (inTeam.size() == 1) {
            return Resolution.of(inTeam.getFirst());
        }
        if (inTeam.size() > 1) {
            List<Player> sameAge = inTeam.stream().filter(sameAge(appearance)).toList();
            return sameAge.size() == 1 ? Resolution.of(sameAge.getFirst()) : Resolution.failed(CrossFailure.AMBIGUOUS);
        }
        List<Player> anywhere = eligible(catalog.named(name)).stream().filter(sameAge(appearance)).toList();
        if (anywhere.size() == 1) {
            return Resolution.of(anywhere.getFirst());
        }
        return Resolution.failed(anywhere.isEmpty() ? CrossFailure.NOT_FOUND : CrossFailure.AMBIGUOUS);
    }

    private List<Player> eligible(List<Player> players) {
        return players.stream()
                .filter(player -> !player.isCrossed())
                .filter(player -> !chosen.contains(player.externalId()))
                .toList();
    }

    /**
     * Un jugador sin fecha de nacimiento, o una aparición sin edad, nunca gana un desempate.
     */
    private Predicate<Player> sameAge(ReportedAppearance appearance) {
        return player -> appearance.age() != null
                && player.ageOn(match.utcDay()).stream().anyMatch(age -> age == appearance.age());
    }

    private Player choose(Player player, ReportedAppearance appearance) {
        chosen.add(player.externalId());
        if (appearance.whoscoredId() == null) {
            return player;
        }
        Player crossed = player.crossWith(appearance.whoscoredId());
        newlyCrossed.add(crossed);
        return crossed;
    }

    private Team teamOf(ReportedAppearance appearance) {
        return appearance.side() == Side.HOME ? match.homeTeam() : match.awayTeam();
    }

    /**
     * El jugador elegido o, si no hay uno solo, el motivo.
     */
    private record Resolution(Player player, CrossFailure failure) {
        static Resolution of(Player player) {
            return new Resolution(player, null);
        }

        static Resolution failed(CrossFailure failure) {
            return new Resolution(null, failure);
        }
    }
}
