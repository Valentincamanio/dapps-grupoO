package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * La región, el torneo y la página de cada liga en WhoScored (contracts/whoscored-pages.md, "Ligas").
 * No cambian entre temporadas; la temporada se filtra por las fechas de la de Football-Data.
 */
public enum WhoScoredTournament {
    PREMIER_LEAGUE(252, 2, "/regions/252/tournaments/2/england-premier-league"),
    BUNDESLIGA(81, 3, "/regions/81/tournaments/3/germany-bundesliga"),
    LALIGA(206, 4, "/regions/206/tournaments/4/spain-laliga"),
    SERIE_A(108, 5, "/regions/108/tournaments/5/italy-serie-a"),
    LIGUE_1(74, 22, "/regions/74/tournaments/22/france-ligue-1");

    private final int regionId;
    private final int tournamentId;
    private final String path;

    WhoScoredTournament(int regionId, int tournamentId, String path) {
        this.regionId = regionId;
        this.tournamentId = tournamentId;
        this.path = path;
    }

    public int regionId() {
        return regionId;
    }

    public int tournamentId() {
        return tournamentId;
    }

    /**
     * La ruta de la página de la liga, relativa a la URL base.
     */
    public String path() {
        return path;
    }

    /**
     * El {@code switch} es exhaustivo: una liga nueva sin torneo no compila.
     */
    public static WhoScoredTournament of(League league) {
        return switch (league) {
            case PREMIER -> PREMIER_LEAGUE;
            case BUNDESLIGA -> BUNDESLIGA;
            case LA_LIGA -> LALIGA;
            case SERIE_A -> SERIE_A;
            case LIGUE_1 -> LIGUE_1;
        };
    }
}
