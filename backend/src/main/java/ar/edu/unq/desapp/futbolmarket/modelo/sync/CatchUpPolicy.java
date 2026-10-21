package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;

/**
 * Decide la puesta al día al arrancar según la última completa exitosa de cada fuente (FR-034 y
 * FR-035; research D22). Una completa está vencida si terminó hace más de {@code maxAge}: con
 * exactamente {@code maxAge} todavía está al día. Sin ninguna registrada, la fuente se considera
 * vencida.
 *
 * <table>
 *   <caption>La decisión</caption>
 *   <tr><th>Football-Data</th><th>WhoScored</th><th>Decisión</th></tr>
 *   <tr><td>Ninguna o vencida</td><td>Cualquiera</td><td>{@link CatchUp#CHAIN}</td></tr>
 *   <tr><td>Al día</td><td>Ninguna o vencida</td><td>{@link CatchUp#WHOSCORED_ONLY}</td></tr>
 *   <tr><td>Al día</td><td>Al día</td><td>{@link CatchUp#NONE}</td></tr>
 * </table>
 */
public record CatchUpPolicy(Duration maxAge) {
    public CatchUpPolicy {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            throw new CatalogInvariantException("La antigüedad máxima de la puesta al día tiene que ser positiva.");
        }
    }

    public CatchUp decide(Optional<LastFullSync> footballData, Optional<LastFullSync> whoScored, Instant now) {
        if (!isUpToDate(footballData, now)) {
            return CatchUp.CHAIN;
        }
        if (!isUpToDate(whoScored, now)) {
            return CatchUp.WHOSCORED_ONLY;
        }
        return CatchUp.NONE;
    }

    private boolean isUpToDate(Optional<LastFullSync> lastFullSync, Instant now) {
        return lastFullSync
                .map(last -> Duration.between(last.finishedAt(), now).compareTo(maxAge) <= 0)
                .orElse(false);
    }
}
