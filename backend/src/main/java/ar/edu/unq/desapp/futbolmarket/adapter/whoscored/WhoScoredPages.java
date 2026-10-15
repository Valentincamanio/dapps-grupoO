package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.BlockedPage;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.PageKind;

/**
 * Las tres páginas de WhoScored que se piden, con su tipo, el id que las identifica y su ruta. El
 * <em>slug</em> final de las rutas es opcional y no se manda.
 */
public final class WhoScoredPages {
    private static final String TEAM_FIXTURES_PATH = "/teams/%s/fixtures";
    private static final String MATCH_PATH = "/matches/%s/live";

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
}
