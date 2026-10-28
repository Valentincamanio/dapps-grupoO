package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketBusyException;

/**
 * Traduce un bloqueo del mercado que no se consigue a tiempo (research D13): otro pedido tiene la
 * fila más que el {@code LOCK_TIMEOUT} de la base. El pedido no se hizo y se puede repetir, así que
 * se informa como {@link MarketBusyException} (409) y no como un error interno. Lo usan los
 * repositories que bloquean: el de órdenes, con el libro del jugador, y el de cuentas.
 */
public final class MarketLocks {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketLocks.class);

    private MarketLocks() {
    }

    public static <T> T orBusy(Supplier<T> lockingQuery) {
        try {
            return lockingQuery.get();
        } catch (PessimisticLockingFailureException e) {
            LOGGER.warn("No se consiguió a tiempo un bloqueo del mercado.", e);
            throw new MarketBusyException();
        }
    }
}
