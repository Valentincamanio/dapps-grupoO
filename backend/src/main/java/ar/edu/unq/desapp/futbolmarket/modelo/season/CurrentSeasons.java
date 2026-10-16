package ar.edu.unq.desapp.futbolmarket.modelo.season;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

/**
 * La temporada en curso de cada liga: la de fecha de inicio más reciente. Cuando Football-Data
 * informa una temporada nueva, la anterior deja de ser la actual y sus partidos no se procesan más
 * (caso borde del cambio de temporada).
 */
public final class CurrentSeasons {
    private final Map<League, Season> currentByLeague;

    private CurrentSeasons(Map<League, Season> currentByLeague) {
        this.currentByLeague = currentByLeague;
    }

    public static CurrentSeasons of(List<Season> seasons) {
        Map<League, Season> currentByLeague = new EnumMap<>(League.class);
        for (Season season : seasons) {
            currentByLeague.merge(season.league(), season, CurrentSeasons::mostRecent);
        }
        return new CurrentSeasons(currentByLeague);
    }

    /**
     * Vacío si el catálogo no tiene ninguna temporada de esa liga.
     */
    public Optional<Season> current(League league) {
        return Optional.ofNullable(currentByLeague.get(league));
    }

    private static Season mostRecent(Season known, Season candidate) {
        return candidate.startDate().isAfter(known.startDate()) ? candidate : known;
    }
}
