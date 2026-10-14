package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Decide qué equipo de WhoScored es cada equipo del catálogo de una liga (FR-016 a FR-018;
 * research D12).
 *
 * <p>Un equipo de WhoScored W y uno del catálogo T coinciden en calendario si al menos
 * {@link #MIN_ALIGNED_FIXTURES} partidos de W se alinean con uno de T de la misma localía y a lo
 * sumo {@link #DATE_TOLERANCE} de diferencia, a lo sumo {@link #MAX_UNALIGNED_FIXTURES} de W quedan
 * sin alinear, y los rivales de las alineaciones forman una correspondencia uno a uno que no
 * contradice ningún cruce.</p>
 *
 * <p>Con un único T sin cruzar del mismo nombre, W se cruza con él solo si coinciden en calendario.
 * Sin ninguno con ese nombre, W se cruza con el único T que coincide. Cada cruce puede habilitar el
 * de un rival, así que la evaluación se repite mientras haya cruces nuevos. Un equipo ya cruzado no
 * se evalúa.</p>
 */
public final class TeamCrossing {
    public static final int MIN_ALIGNED_FIXTURES = 5;
    public static final int MAX_UNALIGNED_FIXTURES = 2;
    public static final Duration DATE_TOLERANCE = Duration.ofDays(1);

    private final Map<String, Team> catalogTeams = new LinkedHashMap<>();
    private final Map<String, String> catalogByWhoscoredId = new HashMap<>();
    private final Map<String, String> sourceNames = new LinkedHashMap<>();
    private final List<Match> seasonMatches;
    private final List<ReportedFixture> fixtures;
    private final List<Team> crossed = new ArrayList<>();
    private final List<UncrossedTeam> uncrossed;

    private TeamCrossing(List<Team> seasonTeams, List<Match> seasonMatches, Collection<ReportedTeam> sourceTeams,
                         Collection<ReportedFixture> fixtures) {
        seasonTeams.forEach(this::register);
        sourceTeams.forEach(team -> sourceNames.putIfAbsent(team.whoscoredId(), team.name()));
        fixtures.forEach(fixture -> {
            sourceNames.putIfAbsent(fixture.homeTeamWhoscoredId(), fixture.homeTeamName());
            sourceNames.putIfAbsent(fixture.awayTeamWhoscoredId(), fixture.awayTeamName());
        });
        this.seasonMatches = seasonMatches.stream().sorted(Comparator.comparing(Match::utcDate)).toList();
        this.fixtures = fixtures.stream().distinct().sorted(Comparator.comparing(ReportedFixture::date)).toList();
        crossWhilePossible();
        this.uncrossed = explainUncrossed();
    }

    /**
     * Los equipos y partidos de la temporada en curso, y lo conocido de WhoScored en la corrida. Las
     * filas ya llegan filtradas por el torneo de la liga y por las fechas de la temporada.
     */
    public static TeamCrossing of(List<Team> seasonTeams, List<Match> seasonMatches,
                                  Collection<ReportedTeam> sourceTeams, Collection<ReportedFixture> fixtures) {
        return new TeamCrossing(seasonTeams, seasonMatches, sourceTeams, fixtures);
    }

    /**
     * Los equipos que quedaron cruzados en esta evaluación, ya con su id de WhoScored.
     */
    public List<Team> crossed() {
        return List.copyOf(crossed);
    }

    /**
     * Cada equipo del catálogo que sigue sin cruzar, con su motivo.
     */
    public List<UncrossedTeam> uncrossed() {
        return uncrossed;
    }

    private void register(Team team) {
        catalogTeams.put(team.externalId(), team);
        if (team.isCrossed()) {
            catalogByWhoscoredId.put(team.whoscoredId(), team.externalId());
        }
    }

    private void crossWhilePossible() {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Map.Entry<String, List<String>> proposal : proposals().entrySet()) {
                List<String> proposers = proposal.getValue();
                if (proposers.size() == 1 && tryCross(proposers.getFirst(), proposal.getKey())) {
                    progress = true;
                }
            }
        }
    }

    /**
     * Para cada equipo del catálogo, los equipos de WhoScored que proponen cruzarse con él. Si lo
     * propone más de uno, no se cruza con ninguno.
     */
    private Map<String, List<String>> proposals() {
        Map<String, List<String>> proposersByTeam = new LinkedHashMap<>();
        for (String whoscoredId : openSourceTeams()) {
            evaluate(whoscoredId).proposal().ifPresent(team -> proposersByTeam
                    .computeIfAbsent(team.externalId(), key -> new ArrayList<>())
                    .add(whoscoredId));
        }
        return proposersByTeam;
    }

    /**
     * Vuelve a verificar el calendario, porque un cruce anterior de la misma vuelta pudo
     * contradecir a este.
     */
    private boolean tryCross(String whoscoredId, String teamExternalId) {
        Team team = catalogTeams.get(teamExternalId);
        if (team.isCrossed() || catalogByWhoscoredId.containsKey(whoscoredId) || !coincide(whoscoredId, team)) {
            return false;
        }
        Team crossedTeam = team.crossWith(whoscoredId);
        register(crossedTeam);
        crossed.add(crossedTeam);
        return true;
    }

    private Evaluation evaluate(String whoscoredId) {
        NormalizedName name = NormalizedName.ofTeam(sourceNames.get(whoscoredId));
        List<Team> open = openCatalogTeams();
        List<Team> sameName = open.stream().filter(team -> NormalizedName.ofTeam(team.name()).equals(name)).toList();
        if (sameName.size() == 1) {
            Team team = sameName.getFirst();
            return coincide(whoscoredId, team)
                    ? Evaluation.proposing(team)
                    : Evaluation.failing(List.of(team), CrossFailure.NOT_CONFIRMED);
        }
        List<Team> coinciding = open.stream().filter(team -> coincide(whoscoredId, team)).toList();
        if (coinciding.size() == 1) {
            return Evaluation.proposing(coinciding.getFirst());
        }
        return Evaluation.failing(coinciding, CrossFailure.AMBIGUOUS);
    }

    private boolean coincide(String whoscoredId, Team team) {
        Alignment alignment = new Alignment();
        Set<String> usedMatches = new HashSet<>();
        for (ReportedFixture fixture : fixturesOf(whoscoredId)) {
            boolean home = whoscoredId.equals(fixture.homeTeamWhoscoredId());
            Optional<Match> match = closestMatch(team, home, fixture.date(), usedMatches);
            if (match.isEmpty()) {
                alignment.unaligned++;
                continue;
            }
            usedMatches.add(match.get().externalId());
            String sourceRival = home ? fixture.awayTeamWhoscoredId() : fixture.homeTeamWhoscoredId();
            String catalogRival = home ? match.get().awayTeam().externalId() : match.get().homeTeam().externalId();
            if (!alignment.addRivals(sourceRival, catalogRival) || contradictsACross(sourceRival, catalogRival)) {
                return false;
            }
        }
        return alignment.aligned >= MIN_ALIGNED_FIXTURES && alignment.unaligned <= MAX_UNALIGNED_FIXTURES;
    }

    private Optional<Match> closestMatch(Team team, boolean home, LocalDate date, Set<String> usedMatches) {
        return seasonMatches.stream()
                .filter(match -> !usedMatches.contains(match.externalId()))
                .filter(match -> team.externalId().equals(home
                        ? match.homeTeam().externalId()
                        : match.awayTeam().externalId()))
                .filter(match -> daysBetween(match.utcDay(), date) <= DATE_TOLERANCE.toDays())
                .min(Comparator.comparingLong(match -> daysBetween(match.utcDay(), date)));
    }

    /**
     * Un rival ya cruzado tiene que ser el mismo en las dos fuentes.
     */
    private boolean contradictsACross(String sourceRival, String catalogRival) {
        Team rival = catalogTeams.get(catalogRival);
        boolean rivalCrossedWithOther = rival != null && rival.isCrossed() && !rival.whoscoredId().equals(sourceRival);
        String crossedTeam = catalogByWhoscoredId.get(sourceRival);
        return rivalCrossedWithOther || (crossedTeam != null && !crossedTeam.equals(catalogRival));
    }

    private List<ReportedFixture> fixturesOf(String whoscoredId) {
        return fixtures.stream()
                .filter(fixture -> whoscoredId.equals(fixture.homeTeamWhoscoredId())
                        || whoscoredId.equals(fixture.awayTeamWhoscoredId()))
                .toList();
    }

    private List<String> openSourceTeams() {
        return sourceNames.keySet().stream().filter(id -> !catalogByWhoscoredId.containsKey(id)).toList();
    }

    private List<Team> openCatalogTeams() {
        return catalogTeams.values().stream().filter(team -> !team.isCrossed()).toList();
    }

    /**
     * Si un equipo junta varios motivos, gana el más informativo: el orden de {@link CrossFailure}
     * va de menos a más informativo.
     */
    private List<UncrossedTeam> explainUncrossed() {
        Map<String, CrossFailure> reasons = new HashMap<>();
        for (String whoscoredId : openSourceTeams()) {
            Evaluation evaluation = evaluate(whoscoredId);
            evaluation.failures().forEach((team, reason) -> reasons.merge(team, reason, TeamCrossing::moreInformative));
            evaluation.proposal().ifPresent(team ->
                    reasons.merge(team.externalId(), CrossFailure.AMBIGUOUS, TeamCrossing::moreInformative));
        }
        return openCatalogTeams().stream()
                .map(team -> new UncrossedTeam(team.externalId(), team.name(),
                        reasons.getOrDefault(team.externalId(), CrossFailure.NOT_FOUND)))
                .toList();
    }

    private static CrossFailure moreInformative(CrossFailure first, CrossFailure second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static long daysBetween(LocalDate first, LocalDate second) {
        return Math.abs(ChronoUnit.DAYS.between(first, second));
    }

    /**
     * Lo que propone un equipo de WhoScored: cruzarse con un equipo del catálogo, o los motivos por
     * los que no puede.
     */
    private record Evaluation(Team proposed, Map<String, CrossFailure> failures) {
        static Evaluation proposing(Team team) {
            return new Evaluation(team, Map.of());
        }

        static Evaluation failing(List<Team> teams, CrossFailure reason) {
            Map<String, CrossFailure> failures = new HashMap<>();
            teams.forEach(team -> failures.put(team.externalId(), reason));
            return new Evaluation(null, failures);
        }

        Optional<Team> proposal() {
            return Optional.ofNullable(proposed);
        }
    }

    /**
     * Las alineaciones de un calendario contra otro y la correspondencia uno a uno de sus rivales.
     */
    private static final class Alignment {
        private final Map<String, String> catalogRivalBySource = new HashMap<>();
        private final Map<String, String> sourceRivalByCatalog = new HashMap<>();
        private int aligned;
        private int unaligned;

        /**
         * Suma una alineación. Es falso si el par de rivales contradice uno anterior.
         */
        boolean addRivals(String sourceRival, String catalogRival) {
            aligned++;
            String knownCatalogRival = catalogRivalBySource.putIfAbsent(sourceRival, catalogRival);
            String knownSourceRival = sourceRivalByCatalog.putIfAbsent(catalogRival, sourceRival);
            return (knownCatalogRival == null || knownCatalogRival.equals(catalogRival))
                    && (knownSourceRival == null || knownSourceRival.equals(sourceRival));
        }
    }
}
