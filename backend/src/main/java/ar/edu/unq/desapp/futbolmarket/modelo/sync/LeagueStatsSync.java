package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Una liga de una corrida de WhoScored (research D11 y D13). Aplica las reglas y acumula los
 * resultados; el servicio la usa paso a paso, pide las páginas y guarda, pero no decide nada.
 *
 * <p>Solo cuentan los partidos de la temporada en curso. Sin temporada en el catálogo, la liga no
 * tiene partidos pendientes y no pide ninguna página.</p>
 *
 * <p>Es mutable y la usa un solo hilo, como {@link SyncRun}.</p>
 */
public final class LeagueStatsSync {
    private static final long SEASON_MARGIN_DAYS = 1;

    private final League league;
    private final Season currentSeason;
    private final Map<String, Team> teams = new LinkedHashMap<>();
    private final Map<String, Match> matches = new LinkedHashMap<>();
    private final Map<String, ReportedTeam> sourceTeams = new LinkedHashMap<>();
    private final Map<String, ReportedFixture> fixtures = new LinkedHashMap<>();
    private final Set<String> requestedPages = new HashSet<>();
    private final Map<String, SkippedStatsMatch> skippedMatches = new LinkedHashMap<>();
    private final List<UncrossedPlayer> uncrossedPlayers = new ArrayList<>();
    private final Map<String, List<NameCross>> nameCrossesByMatch = new HashMap<>();
    private final Map<String, List<BirthDateCross>> birthDateCrossesByMatch = new HashMap<>();
    private final Map<String, List<FullNameCross>> fullNameCrossesByMatch = new HashMap<>();
    private final Map<SnapshotSkipReason, Integer> skippedSnapshotEntries = new EnumMap<>(SnapshotSkipReason.class);
    private List<UncrossedTeam> uncrossedTeams = List.of();
    private int matchesFromSnapshot;
    private int matchesScraped;

    /**
     * {@code currentSeason} es {@code null} si la liga no tiene temporada en el catálogo. Los
     * partidos de otra temporada se descartan.
     */
    public LeagueStatsSync(League league, Season currentSeason, List<Match> seasonMatches) {
        this.league = league;
        this.currentSeason = currentSeason;
        seasonMatches.stream()
                .filter(this::isOfCurrentSeason)
                .sorted(Comparator.comparing(Match::utcDate).thenComparing(Match::externalId))
                .forEach(match -> {
                    teams.putIfAbsent(match.homeTeam().externalId(), match.homeTeam());
                    teams.putIfAbsent(match.awayTeam().externalId(), match.awayTeam());
                    matches.put(match.externalId(), match);
                });
    }

    /**
     * Los locales y visitantes de los partidos de la temporada, sin repetir y en el orden en que
     * aparecen en el calendario.
     */
    public List<Team> seasonTeams() {
        return List.copyOf(teams.values());
    }

    /**
     * Los partidos terminados de la temporada que todavía no tienen estadísticas, del más antiguo al
     * más reciente (FR-010 y FR-011).
     */
    public List<Match> pendingMatches() {
        return matches.values().stream().filter(Match::needsStats).toList();
    }

    /**
     * Cuenta los partidos guardados desde el snapshot, que dejan de estar pendientes, y suma las
     * entradas salteadas.
     */
    public void recordSnapshot(SnapshotLoad load, List<MatchStatsLoad> saved) {
        matchesFromSnapshot += saved.size();
        saved.forEach(loaded -> update(loaded.match()));
        load.skipped().forEach((reason, count) -> skippedSnapshotEntries.merge(reason, count, Integer::sum));
    }

    /**
     * La página de la liga solo hace falta para descubrir equipos: con todos cruzados, no aporta.
     */
    public boolean needsLeaguePage() {
        return !pendingMatches().isEmpty() && teams.values().stream().anyMatch(team -> !team.isCrossed());
    }

    public void registerSourceTeams(List<ReportedTeam> reported) {
        reported.forEach(team -> sourceTeams.putIfAbsent(team.whoscoredId(), team));
    }

    /**
     * El id de WhoScored del próximo equipo cuya página de fixtures conviene pedir, nunca dos veces
     * el mismo en la corrida. Primero, mientras quede algún equipo del catálogo sin cruzar, los
     * equipos de WhoScored que no corresponden a ninguno, en el orden en que se conocieron. Después,
     * el equipo cruzado que cubre más partidos pendientes sin id con sus dos equipos cruzados; a
     * igual cantidad, el que aparece primero en el calendario.
     */
    public Optional<String> nextFixturesPage() {
        if (pendingMatches().isEmpty()) {
            return Optional.empty();
        }
        Optional<String> unmatched = unmatchedSourceTeam();
        return unmatched.isPresent() ? unmatched : teamCoveringMostPendingMatches();
    }

    /**
     * Las filas fuera de las fechas de la temporada, con un día de margen, son de otra temporada y
     * se descartan (FR-003). Los dos equipos de cada fila pasan a ser equipos conocidos.
     */
    public void registerFixtures(String teamWhoscoredId, List<ReportedFixture> rows) {
        requestedPages.add(teamWhoscoredId);
        rows.stream()
                .filter(row -> row.whoscoredId() != null && isWithinSeason(row.date()))
                .forEach(row -> {
                    fixtures.putIfAbsent(row.whoscoredId(), row);
                    sourceTeams.putIfAbsent(row.homeTeamWhoscoredId(),
                            new ReportedTeam(row.homeTeamWhoscoredId(), row.homeTeamName()));
                    sourceTeams.putIfAbsent(row.awayTeamWhoscoredId(),
                            new ReportedTeam(row.awayTeamWhoscoredId(), row.awayTeamName()));
                });
    }

    /**
     * Los equipos que quedan cruzados con lo conocido hasta ahora. Se toman como estado actual recién
     * con {@link #confirmTeams}, cuando están guardados.
     */
    public List<Team> teamCrosses() {
        TeamCrossing crossing = TeamCrossing.of(seasonTeams(), List.copyOf(matches.values()), sourceTeams.values(),
                fixtures.values());
        uncrossedTeams = crossing.uncrossed();
        return crossing.crossed();
    }

    /**
     * Los partidos sin id con sus dos equipos cruzados que tienen una única fila con ese local y ese
     * visitante (FR-015). No se mira la fecha: en una liga, esos dos equipos se enfrentan una sola
     * vez con esa localía.
     */
    public List<Match> matchCrosses() {
        Set<String> takenIds = matches.values().stream()
                .filter(Match::isCrossed)
                .map(Match::whoscoredId)
                .collect(Collectors.toCollection(HashSet::new));
        List<Match> crossed = new ArrayList<>();
        for (Match match : matches.values()) {
            uniqueRowFor(match)
                    .filter(row -> takenIds.add(row.whoscoredId()))
                    .ifPresent(row -> crossed.add(match.crossWith(row.whoscoredId())));
        }
        return crossed;
    }

    public void confirmTeams(List<Team> saved) {
        saved.stream()
                .filter(team -> teams.containsKey(team.externalId()))
                .forEach(team -> teams.put(team.externalId(), team));
        matches.replaceAll((externalId, match) -> withCurrentTeams(match));
    }

    public void confirmMatches(List<Match> saved) {
        saved.forEach(this::update);
    }

    /**
     * Omite los pendientes que siguen sin id: les falta un equipo cruzado, o sus dos equipos están
     * cruzados pero WhoScored no tiene una fila con ese local y ese visitante (FR-014 y FR-018).
     */
    public void closeDiscovery() {
        pendingMatches().stream()
                .filter(match -> !match.isCrossed())
                .forEach(match -> skip(match, bothTeamsCrossed(match)
                        ? StatsSkipReason.NOT_IN_SOURCE
                        : StatsSkipReason.UNCROSSED_TEAM));
    }

    /**
     * Los pendientes con id, del más antiguo al más reciente.
     */
    public List<Match> matchesToScrape() {
        return pendingMatches().stream().filter(Match::isCrossed).toList();
    }

    /**
     * Lo que se guarda de un partido scrapeado: las estadísticas de los que entraron y se cruzaron.
     * Sin datos o sin terminar en WhoScored, o con otros equipos, el partido se omite. Los jugadores
     * sin cruzar van al informe (FR-021).
     */
    public Optional<MatchStatsLoad> prepare(Match match, Optional<ReportedMatchStats> reported,
                                            CatalogPlayers catalog) {
        return prepare(match, reported, catalog, Map.of());
    }

    /**
     * Como {@link #prepare(Match, Optional, CatalogPlayers)}, con las páginas de jugador, por id de
     * WhoScored, para los que los nombres no cruzan (FR-019d).
     */
    public Optional<MatchStatsLoad> prepare(Match match, Optional<ReportedMatchStats> reported,
                                            CatalogPlayers catalog, Map<String, ReportedPlayerProfile> profiles) {
        Match current = matches.getOrDefault(match.externalId(), match);
        if (reported.isEmpty() || !reported.get().finished()) {
            skip(current, StatsSkipReason.NOT_FINISHED_IN_SOURCE);
            return Optional.empty();
        }
        ReportedMatchStats stats = reported.get();
        if (!stats.isFor(current)) {
            skip(current, StatsSkipReason.NOT_IN_SOURCE);
            return Optional.empty();
        }
        Match loaded = current.markStatsLoaded();
        PlayerCrossing crossing = new PlayerCrossing(catalog, loaded, stats, profiles);
        List<PlayerMatchStats> playerStats = new ArrayList<>();
        for (ReportedAppearance appearance : stats.onPitch()) {
            crossing.cross(appearance).ifPresent(player -> playerStats.add(stats.toStats(appearance, player, loaded)));
        }
        uncrossedPlayers.addAll(crossing.uncrossed());
        nameCrossesByMatch.put(loaded.externalId(), crossing.nameCrosses());
        birthDateCrossesByMatch.put(loaded.externalId(), crossing.birthDateCrosses());
        fullNameCrossesByMatch.put(loaded.externalId(), crossing.fullNameCrosses());
        return Optional.of(new MatchStatsLoad(loaded, crossing.newlyCrossed(), playerStats, LoadSource.SCRAPING));
    }

    /**
     * Los ids de WhoScored de los jugadores del partido que los nombres dejarían sin cruzar: los que
     * conviene confirmar con su página de jugador antes de {@link #prepare} (FR-019d). No cambia nada
     * de la liga. Vacío si el partido se omitiría.
     */
    public List<String> birthDatesNeeded(Match match, Optional<ReportedMatchStats> reported,
                                         CatalogPlayers catalog) {
        Match current = matches.getOrDefault(match.externalId(), match);
        if (reported.isEmpty() || !reported.get().finished() || !reported.get().isFor(current)) {
            return List.of();
        }
        PlayerCrossing crossing = new PlayerCrossing(catalog, current, reported.get());
        reported.get().onPitch().forEach(crossing::cross);
        return crossing.needsBirthDate();
    }

    /**
     * Los cruces por fecha de nacimiento que dejó {@link #prepare} para el partido, con el mismo uso
     * que {@link #nameCrossesOf}. Vacío si no hubo.
     */
    public List<BirthDateCross> birthDateCrossesOf(Match match) {
        return birthDateCrossesByMatch.getOrDefault(match.externalId(), List.of());
    }

    /**
     * Los cruces por nombre completo y año de nacimiento que dejó {@link #prepare} para el partido,
     * con el mismo uso que {@link #nameCrossesOf}. Vacío si no hubo.
     */
    public List<FullNameCross> fullNameCrossesOf(Match match) {
        return fullNameCrossesByMatch.getOrDefault(match.externalId(), List.of());
    }

    /**
     * Los cruces por nivel de nombre que dejó {@link #prepare} para el partido, para que el servicio
     * los escriba en el registro al guardarlo (FR-019b). Vacío si no hubo.
     */
    public List<NameCross> nameCrossesOf(Match match) {
        return nameCrossesByMatch.getOrDefault(match.externalId(), List.of());
    }

    public void recordScraped(MatchStatsLoad load) {
        matchesScraped++;
        update(load.match());
    }

    public void recordWriteFailure(Match match) {
        skip(match, StatsSkipReason.WRITE_FAILED);
    }

    /**
     * Un valor imposible de la fuente, como un rating fuera de 0 a 10 (research D18).
     */
    public void recordInvalidSource(Match match) {
        skip(match, StatsSkipReason.INVALID_IN_SOURCE);
    }

    /**
     * Una liga a la que el scraping no llegó informa solo lo que cargó del snapshot.
     */
    public LeagueStatsResult result(LeagueStatsStatus status) {
        if (status == LeagueStatsStatus.NOT_REACHED) {
            return LeagueStatsResult.notReached(league, matchesFromSnapshot, skippedSnapshotEntries);
        }
        return new LeagueStatsResult(league, status, matchesFromSnapshot, matchesScraped,
                List.copyOf(skippedMatches.values()), uncrossedTeams, uncrossedPlayers, skippedSnapshotEntries);
    }

    private Optional<String> unmatchedSourceTeam() {
        if (teams.values().stream().allMatch(Team::isCrossed)) {
            return Optional.empty();
        }
        Set<String> crossedIds = teams.values().stream()
                .filter(Team::isCrossed)
                .map(Team::whoscoredId)
                .collect(Collectors.toSet());
        return sourceTeams.keySet().stream()
                .filter(id -> !crossedIds.contains(id) && !requestedPages.contains(id))
                .findFirst();
    }

    private Optional<String> teamCoveringMostPendingMatches() {
        Map<String, Integer> coverage = new LinkedHashMap<>();
        teams.values().stream()
                .filter(team -> team.isCrossed() && !requestedPages.contains(team.whoscoredId()))
                .forEach(team -> coverage.put(team.externalId(), 0));
        pendingMatches().stream()
                .filter(match -> !match.isCrossed() && bothTeamsCrossed(match))
                .forEach(match -> {
                    coverage.computeIfPresent(match.homeTeam().externalId(), (team, count) -> count + 1);
                    coverage.computeIfPresent(match.awayTeam().externalId(), (team, count) -> count + 1);
                });
        return coverage.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(entry -> teams.get(entry.getKey()).whoscoredId());
    }

    private Optional<ReportedFixture> uniqueRowFor(Match match) {
        if (match.isCrossed() || !bothTeamsCrossed(match)) {
            return Optional.empty();
        }
        String home = teams.get(match.homeTeam().externalId()).whoscoredId();
        String away = teams.get(match.awayTeam().externalId()).whoscoredId();
        List<ReportedFixture> rows = fixtures.values().stream()
                .filter(row -> home.equals(row.homeTeamWhoscoredId()) && away.equals(row.awayTeamWhoscoredId()))
                .toList();
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }

    private boolean bothTeamsCrossed(Match match) {
        return teams.get(match.homeTeam().externalId()).isCrossed()
                && teams.get(match.awayTeam().externalId()).isCrossed();
    }

    private void update(Match match) {
        if (matches.containsKey(match.externalId())) {
            matches.put(match.externalId(), withCurrentTeams(match));
        }
    }

    private Match withCurrentTeams(Match match) {
        return new Match(match.id(), match.externalId(), match.season(), match.utcDate(), match.matchday(),
                match.status(), teams.getOrDefault(match.homeTeam().externalId(), match.homeTeam()),
                teams.getOrDefault(match.awayTeam().externalId(), match.awayTeam()), match.fullTime(),
                match.halfTime(), match.winner(), match.whoscoredId(), match.statsLoaded());
    }

    private void skip(Match match, StatsSkipReason reason) {
        Match current = matches.getOrDefault(match.externalId(), match);
        skippedMatches.put(current.externalId(), new SkippedStatsMatch(current.externalId(),
                current.homeTeam().name(), current.awayTeam().name(), current.utcDate(), reason));
    }

    private boolean isOfCurrentSeason(Match match) {
        return currentSeason != null && match.season().externalId().equals(currentSeason.externalId());
    }

    private boolean isWithinSeason(LocalDate date) {
        return currentSeason != null
                && !date.isBefore(currentSeason.startDate().minusDays(SEASON_MARGIN_DAYS))
                && !date.isAfter(currentSeason.endDate().plusDays(SEASON_MARGIN_DAYS));
    }
}
