package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.BlockedPage;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.PageKind;

/**
 * Las páginas de WhoScored que se piden: las tres que pueden cortar una corrida, con su tipo, el id
 * que las identifica y su ruta, y la de un jugador, que nunca la corta y por eso es solo una ruta
 * (research D28). El <em>slug</em> final de las rutas es opcional y no se manda.
 */
public final class WhoScoredPages {
    private static final String TEAM_FIXTURES_PATH = "/teams/%s/fixtures";
    private static final String MATCH_PATH = "/matches/%s/live";
    private static final String PLAYER_PATH = "/players/%s/show";

    private WhoScoredPages() {
    }

    /**
     * La página de la liga, identificada por su torneo.
     */
    public static BlockedPage league(League league) {
        WhoScoredTournament tournament = WhoScoredTournament.of(league);
        return new BlockedPage(PageKind.LEAGUE, String.valueOf(tournament.tournamentId()), tournament.path());
    }

    public static BlockedPage teamFixtures(String teamId) {
        return new BlockedPage(PageKind.TEAM_FIXTURES, teamId, TEAM_FIXTURES_PATH.formatted(teamId));
    }

    public static BlockedPage match(String matchId) {
        return new BlockedPage(PageKind.MATCH, matchId, MATCH_PATH.formatted(matchId));
    }

    /**
     * La ruta de la página de un jugador, de la que se lee su fecha de nacimiento (FR-019d).
     */
    public static String playerPath(String playerId) {
        return PLAYER_PATH.formatted(playerId);
    }
}
