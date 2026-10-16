package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * catálogo de ese lado con el mismo nombre normalizado, desempatando por la edad. Si en el equipo no
 * hay ninguno, busca en todo el catálogo por nombre y misma edad: si hay alguno, el nombre y la
 * edad no alcanzan para cruzarlo con un jugador de otro equipo, y queda sin confirmar para que lo
 * resuelva su página de jugador (FR-019a). Si no hay ninguno, en el equipo por los niveles de
 * {@link NameMatchLevel}, en orden y con la misma edad: con un candidato se cruza, y con más de uno
 * es ambiguo sin bajar de nivel (FR-019b). Nunca es candidato un jugador con otro id de WhoScored ni
 * uno ya elegido en el mismo partido. Nunca se crea un jugador.</p>
 *
 * <p>WhoScored informa la edad del día en que se sirve la página, no la del partido (FR-019c): un
 * jugador es de la misma edad si la que surge de su fecha de nacimiento el día del partido es la
 * informada o una menos.</p>
 *
 * <p>Si con los nombres queda sin cruzar y su página de jugador trajo la fecha de nacimiento, se
 * prueban en orden los pasos de FR-019d: los de su equipo con exactamente esa fecha y alguna
 * relación de nombre (una palabra en común, un nivel sin la edad o el nombre dentro del nombre
 * completo), porque dos compañeros pueden nacer el mismo día; los de otros equipos con esa fecha
 * y un nombre que coincide por los niveles 1 a 3 o está dentro del nombre completo; y los de su
 * equipo nacidos el mismo año cuyo nombre está dentro del nombre completo.
 * Los que quedan sin cruzar y no tenían página están en {@link #needsBirthDate()}.</p>
 *
 * <p>Es mutable y lo usa un solo hilo, un partido por instancia.</p>
 */
public final class PlayerCrossing {
    /**
     * Cuántos años más puede informar WhoScored que la edad del día del partido: una temporada dura
     * menos de un año.
     */
    private static final int YEARS_BETWEEN_MATCH_AND_PAGE = 1;

    /**
     * Los niveles que valen para un jugador de otro equipo con la misma fecha de nacimiento
     * (FR-019d, paso 2): los más seguros.
     */
    private static final Set<NameMatchLevel> OTHER_TEAM_LEVELS =
            EnumSet.range(NameMatchLevel.SAME_WORDS, NameMatchLevel.SAME_SURNAME);

    private final CatalogPlayers catalog;
    private final Match match;
    private final Map<String, ReportedPlayerProfile> profiles;
    private final Set<String> chosen = new HashSet<>();
    private final List<Player> newlyCrossed = new ArrayList<>();
    private final List<NameCross> nameCrosses = new ArrayList<>();
    private final List<BirthDateCross> birthDateCrosses = new ArrayList<>();
    private final List<FullNameCross> fullNameCrosses = new ArrayList<>();
    private final List<String> needsBirthDate = new ArrayList<>();
    private final List<UncrossedPlayer> uncrossed = new ArrayList<>();

    public PlayerCrossing(CatalogPlayers catalog, Match match, ReportedMatchStats reported) {
        this(catalog, match, reported, Map.of());
    }

    /**
     * {@code profiles} son las páginas de jugador que trajo WhoScored, por id de WhoScored.
     */
    public PlayerCrossing(CatalogPlayers catalog, Match match, ReportedMatchStats reported,
                          Map<String, ReportedPlayerProfile> profiles) {
        this.catalog = Objects.requireNonNull(catalog);
        this.match = Objects.requireNonNull(match);
        Objects.requireNonNull(reported);
        this.profiles = Map.copyOf(profiles);
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
        if (resolution.player() == null) {
            resolution = byPlayerPage(appearance, resolution);
        }
        if (resolution.player() != null) {
            Player crossed = choose(resolution.player(), appearance);
            if (resolution.level() != null) {
                nameCrosses.add(new NameCross(appearance.name(), crossed, resolution.level()));
            }
            if (resolution.birthDate() != null) {
                birthDateCrosses.add(new BirthDateCross(appearance.name(), crossed, resolution.birthDate()));
            }
            if (resolution.fullName() != null) {
                fullNameCrosses.add(new FullNameCross(appearance.name(), resolution.fullName(), crossed));
            }
            return Optional.of(crossed);
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

    /**
     * Los cruces de este partido hechos por un nivel de nombre, para el registro.
     */
    public List<NameCross> nameCrosses() {
        return List.copyOf(nameCrosses);
    }

    /**
     * Los cruces de este partido hechos por la fecha de nacimiento (FR-019d, pasos 1 y 2), para el
     * registro.
     */
    public List<BirthDateCross> birthDateCrosses() {
        return List.copyOf(birthDateCrosses);
    }

    /**
     * Los cruces de este partido hechos por el nombre completo y el año de nacimiento (FR-019d, paso
     * 3), para el registro.
     */
    public List<FullNameCross> fullNameCrosses() {
        return List.copyOf(fullNameCrosses);
    }

    /**
     * Los ids de WhoScored de los que quedaron sin cruzar, incluidos los que FR-019a dejó sin
     * confirmar, y no tenían página: los que conviene resolver con su página de jugador (FR-019d).
     */
    public List<String> needsBirthDate() {
        return List.copyOf(needsBirthDate);
    }

    public List<UncrossedPlayer> uncrossed() {
        return List.copyOf(uncrossed);
    }

    /**
     * En el equipo, con más de un homónimo desempata la edad, y si no la resuelve es ambiguo. Fuera
     * del equipo, el nombre y la misma edad no alcanzan: con un candidato queda sin confirmar, y con
     * más, ambiguo, y en los dos casos no se prueban los niveles, que podrían elegir a un compañero
     * parecido. Sin nadie con ese nombre, los niveles.
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
            return Resolution.failed(CrossFailure.NOT_CONFIRMED);
        }
        if (anywhere.size() > 1) {
            return Resolution.failed(CrossFailure.AMBIGUOUS);
        }
        return byNameLevels(appearance, name);
    }

    /**
     * Los niveles de FR-019b sobre los jugadores del equipo que pueden ser candidatos y son de la
     * misma edad. Un nivel con más de un candidato no deja bajar al siguiente, que elegiría entre
     * candidatos que este ya no pudo distinguir.
     */
    private Resolution byNameLevels(ReportedAppearance appearance, NormalizedName name) {
        List<Player> candidates = eligible(catalog.inTeam(teamOf(appearance))).stream()
                .filter(sameAge(appearance))
                .toList();
        for (NameMatchLevel level : NameMatchLevel.values()) {
            List<Player> matching = candidates.stream()
                    .filter(player -> level.matches(name, NormalizedName.ofPlayer(player.name())))
                    .toList();
            if (matching.size() == 1) {
                return Resolution.byLevel(matching.getFirst(), level);
            }
            if (matching.size() > 1) {
                return Resolution.failed(CrossFailure.AMBIGUOUS);
            }
        }
        return Resolution.failed(CrossFailure.NOT_FOUND);
    }

    /**
     * Sin página, queda pedirla. Con la fecha, los pasos de FR-019d en orden: el primero que
     * encuentra a alguien decide, y con más de uno queda el motivo de los nombres.
     */
    private Resolution byPlayerPage(ReportedAppearance appearance, Resolution failed) {
        if (appearance.whoscoredId() == null) {
            return failed;
        }
        ReportedPlayerProfile profile = profiles.get(appearance.whoscoredId());
        if (profile == null) {
            needsBirthDate.add(appearance.whoscoredId());
            return failed;
        }
        LocalDate birthDate = profile.birthDate();
        if (birthDate == null) {
            return failed;
        }
        Team team = teamOf(appearance);
        NormalizedName reported = NormalizedName.ofPlayer(appearance.name());
        Optional<NormalizedName> fullName = Optional.ofNullable(profile.fullName())
                .map(NormalizedName::ofPlayer)
                .filter(name -> !name.value().isEmpty());
        List<Player> inTeam = bornOn(catalog.inTeam(team), birthDate).stream()
                .filter(nameRelated(reported, fullName))
                .toList();
        if (!inTeam.isEmpty()) {
            return inTeam.size() == 1 ? Resolution.byBirthDate(inTeam.getFirst(), birthDate) : failed;
        }
        List<Player> otherTeams = eligible(catalog.bornOn(birthDate)).stream()
                .filter(sameNameOrWithin(reported, fullName))
                .toList();
        if (!otherTeams.isEmpty()) {
            return otherTeams.size() == 1 ? Resolution.byBirthDate(otherTeams.getFirst(), birthDate) : failed;
        }
        if (fullName.isEmpty()) {
            return failed;
        }
        List<Player> sameYear = eligible(catalog.inTeam(team)).stream()
                .filter(player -> player.dateOfBirth() != null && player.dateOfBirth().getYear() == birthDate.getYear())
                .filter(player -> NormalizedName.ofPlayer(player.name()).isWithin(fullName.get()))
                .toList();
        return sameYear.size() == 1 ? Resolution.byFullName(sameYear.getFirst(), profile.fullName()) : failed;
    }

    /**
     * Para un compañero nacido el mismo día (FR-019d, paso 1): alguna relación de nombre, porque
     * dos jugadores del mismo equipo pueden nacer el mismo día. Comparten una palabra, coinciden
     * por algún nivel sin la condición de la edad, o su nombre está dentro del nombre completo.
     */
    private static Predicate<Player> nameRelated(NormalizedName reported, Optional<NormalizedName> fullName) {
        return player -> {
            NormalizedName name = NormalizedName.ofPlayer(player.name());
            return name.sharesWordWith(reported)
                    || Arrays.stream(NameMatchLevel.values()).anyMatch(level -> level.matches(reported, name))
                    || fullName.filter(name::isWithin).isPresent();
        };
    }

    /**
     * Para un jugador de otro equipo nacido el mismo día (FR-019d, paso 2): su nombre coincide con
     * el de WhoScored por los niveles 1 a 3, o está dentro del nombre completo.
     */
    private static Predicate<Player> sameNameOrWithin(NormalizedName reported, Optional<NormalizedName> fullName) {
        return player -> {
            NormalizedName name = NormalizedName.ofPlayer(player.name());
            return OTHER_TEAM_LEVELS.stream().anyMatch(level -> level.matches(reported, name))
                    || fullName.filter(name::isWithin).isPresent();
        };
    }

    private List<Player> bornOn(List<Player> players, LocalDate birthDate) {
        return eligible(players).stream()
                .filter(player -> birthDate.equals(player.dateOfBirth()))
                .toList();
    }

    private List<Player> eligible(List<Player> players) {
        return players.stream()
                .filter(player -> !player.isCrossed())
                .filter(player -> !chosen.contains(player.externalId()))
                .toList();
    }

    /**
     * La edad informada es la del día de la página: la del partido o una más (FR-019c). Un jugador
     * sin fecha de nacimiento, o una aparición sin edad, nunca es de la misma edad.
     */
    private Predicate<Player> sameAge(ReportedAppearance appearance) {
        return player -> appearance.age() != null
                && player.ageOn(match.utcDay()).stream().anyMatch(age -> appearance.age() >= age
                        && appearance.age() - age <= YEARS_BETWEEN_MATCH_AND_PAGE);
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
     * El jugador elegido, con el nivel de nombre, la fecha de nacimiento o el nombre completo si lo
     * eligió uno de ellos, o, si no hay uno solo, el motivo.
     */
    private record Resolution(Player player, NameMatchLevel level, LocalDate birthDate, String fullName,
                              CrossFailure failure) {
        static Resolution of(Player player) {
            return new Resolution(player, null, null, null, null);
        }

        static Resolution byLevel(Player player, NameMatchLevel level) {
            return new Resolution(player, level, null, null, null);
        }

        static Resolution byBirthDate(Player player, LocalDate birthDate) {
            return new Resolution(player, null, birthDate, null, null);
        }

        static Resolution byFullName(Player player, String fullName) {
            return new Resolution(player, null, null, fullName, null);
        }

        static Resolution failed(CrossFailure failure) {
            return new Resolution(null, null, null, null, failure);
        }
    }
}
