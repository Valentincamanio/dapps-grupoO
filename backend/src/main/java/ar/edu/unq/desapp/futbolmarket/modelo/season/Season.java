package ar.edu.unq.desapp.futbolmarket.modelo.season;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

/**
 * Temporada de una liga. Una temporada con otro {@code externalId} es otra temporada: la anterior
 * se conserva con sus partidos (FR-025).
 */
public record Season(Long id, String externalId, League league, LocalDate startDate, LocalDate endDate,
                     Integer currentMatchday) {
    private static final int FIRST_MATCHDAY = 1;

    public Season {
        if (externalId == null || externalId.isBlank()) {
            throw new CatalogInvariantException("El identificador externo de la temporada es obligatorio.");
        }
        if (league == null) {
            throw new CatalogInvariantException("La liga de la temporada es obligatoria.");
        }
        if (startDate == null) {
            throw new CatalogInvariantException("La fecha de inicio de la temporada es obligatoria.");
        }
        if (endDate == null) {
            throw new CatalogInvariantException("La fecha de fin de la temporada es obligatoria.");
        }
        if (endDate.isBefore(startDate)) {
            throw new CatalogInvariantException("La fecha de fin de la temporada no puede ser anterior a la de inicio.");
        }
        if (currentMatchday != null && currentMatchday < FIRST_MATCHDAY) {
            throw new CatalogInvariantException("La jornada actual de la temporada tiene que ser mayor o igual a 1.");
        }
        externalId = externalId.trim();
    }

    /**
     * Conserva la identidad y la liga, y toma las fechas y la jornada que informa la fuente
     * (FR-020 y FR-021).
     */
    public Season updateFrom(Season reported) {
        return new Season(id, externalId, league, reported.startDate(), reported.endDate(), reported.currentMatchday());
    }
}
