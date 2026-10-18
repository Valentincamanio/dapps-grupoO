package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.Instant;

import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

/**
 * Cuándo terminó la última sincronización completa exitosa de una fuente. Es lo único que se
 * guarda de las corridas (FR-047 y FR-054).
 */
public record LastFullSync(SyncSource source, Instant finishedAt) {
    public LastFullSync {
        if (source == null) {
            throw new CatalogInvariantException("La fuente de la última sincronización completa es obligatoria.");
        }
        if (finishedAt == null) {
            throw new CatalogInvariantException(
                    "El momento en que terminó la última sincronización completa es obligatorio.");
        }
    }
}
