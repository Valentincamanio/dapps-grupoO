package ar.edu.unq.desapp.futbolmarket.modelo.match;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Partido de una temporada. El resultado y el ganador faltan hasta que el partido se juega
 * (FR-022).
 *
 * <p>{@code whoscoredId} queda en {@code null} hasta que el partido se cruza con WhoScored (FR-015).
 * {@code statsLoaded} pasa a {@code true} cuando se guardan sus estadísticas y nunca vuelve a
 * {@code false} (FR-012).</p>
 */
public record Match(Long id, String externalId, Season season, Instant utcDate, Integer matchday, MatchStatus status,
                    Team homeTeam, Team awayTeam, Score fullTime, Score halfTime, MatchWinner winner,
                    String whoscoredId, boolean statsLoaded) {
    static final String HOME_TEAM_REQUIRED = "El equipo local del partido es obligatorio.";
    static final String AWAY_TEAM_REQUIRED = "El equipo visitante del partido es obligatorio.";

    public Match {
        externalId = requireExternalId(externalId);
        if (season == null) {
            throw new CatalogInvariantException("La temporada del partido es obligatoria.");
        }
        requireUtcDate(utcDate);
        if (status == null) {
            throw new CatalogInvariantException("El estado del partido es obligatorio.");
        }
        if (homeTeam == null) {
            throw new CatalogInvariantException(HOME_TEAM_REQUIRED);
        }
        if (awayTeam == null) {
            throw new CatalogInvariantException(AWAY_TEAM_REQUIRED);
        }
        requireDifferentTeams(homeTeam.externalId(), awayTeam.externalId());
        whoscoredId = trimToNull(whoscoredId);
    }

    public Match(Long id, String externalId, Season season, Instant utcDate, Integer matchday, MatchStatus status,
                 Team homeTeam, Team awayTeam, Score fullTime, Score halfTime, MatchWinner winner) {
        this(id, externalId, season, utcDate, matchday, status, homeTeam, awayTeam, fullTime, halfTime, winner,
                null, false);
    }

    /**
     * Conserva la identidad y la temporada, y toma lo demás de la fuente. Un partido postergado o
     * que pasó a {@code FINISHED} es el mismo partido, actualizado (HU2, escenario 4). También
     * conserva el cruce con WhoScored y la marca de estadísticas.
     */
    public Match updateFrom(MatchSnapshot snapshot, Team home, Team away) {
        return new Match(id, externalId, season, snapshot.utcDate(), snapshot.matchday(), snapshot.status(),
                home, away, snapshot.fullTime(), snapshot.halfTime(), snapshot.winner(), whoscoredId, statsLoaded);
    }

    /**
     * Un partido terminado que todavía no tiene estadísticas (FR-010 y FR-011). Que sea de la
     * temporada en curso lo decide la sincronización de la liga.
     */
    public boolean needsStats() {
        return status == MatchStatus.FINISHED && !statsLoaded;
    }

    public boolean isCrossed() {
        return whoscoredId != null;
    }

    /**
     * El partido cruzado con ese identificador de WhoScored. Volver a cruzarlo con el mismo no
     * cambia nada; con otro, rompería un cruce que tiene que ser para siempre.
     */
    public Match crossWith(String whoscoredId) {
        Match crossed = new Match(id, externalId, season, utcDate, matchday, status, homeTeam, awayTeam, fullTime,
                halfTime, winner, whoscoredId, statsLoaded);
        if (isCrossed() && !this.whoscoredId.equals(crossed.whoscoredId())) {
            throw new CatalogInvariantException("El partido ya está cruzado con otro identificador de WhoScored.");
        }
        return crossed;
    }

    /**
     * El partido con sus estadísticas ya cargadas. No hay operación inversa: un partido con
     * estadísticas no se vuelve a procesar (FR-012).
     */
    public Match markStatsLoaded() {
        return new Match(id, externalId, season, utcDate, matchday, status, homeTeam, awayTeam, fullTime, halfTime,
                winner, whoscoredId, true);
    }

    /**
     * El día UTC del partido, para comparar calendarios con las fechas de WhoScored.
     */
    public LocalDate utcDay() {
        return LocalDate.ofInstant(utcDate, ZoneOffset.UTC);
    }

    /**
     * Compartido con {@link MatchSnapshot}, para que un partido informado por la fuente y uno
     * guardado tengan los mismos invariantes y los mismos mensajes.
     */
    static String requireExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            throw new CatalogInvariantException("El identificador externo del partido es obligatorio.");
        }
        return externalId.trim();
    }

    static void requireUtcDate(Instant utcDate) {
        if (utcDate == null) {
            throw new CatalogInvariantException("La fecha y la hora del partido son obligatorias.");
        }
    }

    static void requireDifferentTeams(String homeTeamExternalId, String awayTeamExternalId) {
        if (homeTeamExternalId.equals(awayTeamExternalId)) {
            throw new CatalogInvariantException("El equipo local y el visitante de un partido tienen que ser distintos.");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
