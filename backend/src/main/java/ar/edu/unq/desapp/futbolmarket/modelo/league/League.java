package ar.edu.unq.desapp.futbolmarket.modelo.league;

import java.util.Arrays;

import ar.edu.unq.desapp.futbolmarket.modelo.league.exception.UnsupportedLeagueException;

public enum League {
    PREMIER,
    BUNDESLIGA,
    LA_LIGA,
    SERIE_A,
    LIGUE_1;

    /**
     * La liga con ese nombre. Recorta los espacios de los extremos y distingue mayúsculas, igual que
     * el enlace de enums de Spring: {@code " PREMIER "} es la Premier y {@code "premier"} no. Que el
     * valor sea una de las cinco ligas es una invariante del dominio (FR-030 y research D15).
     */
    public static League fromName(String value) {
        String name = value == null ? null : value.trim();
        return Arrays.stream(values())
                .filter(league -> league.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new UnsupportedLeagueException(value));
    }
}
