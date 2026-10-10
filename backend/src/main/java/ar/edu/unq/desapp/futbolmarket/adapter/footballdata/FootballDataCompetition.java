package ar.edu.unq.desapp.futbolmarket.adapter.footballdata;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * El código con el que la fuente identifica a cada liga. El nombre de cada constante es el código.
 */
public enum FootballDataCompetition {
    PL,
    BL1,
    PD,
    SA,
    FL1;

    public String code() {
        return name();
    }

    /**
     * El {@code switch} es exhaustivo: una liga nueva sin código no compila.
     */
    public static FootballDataCompetition of(League league) {
        return switch (league) {
            case PREMIER -> PL;
            case BUNDESLIGA -> BL1;
            case LA_LIGA -> PD;
            case SERIE_A -> SA;
            case LIGUE_1 -> FL1;
        };
    }
}
