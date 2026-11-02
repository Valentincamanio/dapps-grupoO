package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.InsufficientTokensException;
import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import lombok.Getter;

/**
 * Los tokens de un jugador que tiene un usuario (research D12). El superusuario no tiene: su
 * tenencia es el pendiente de su orden inicial (research D7).
 *
 * <p>Los tokens comprometidos son los de sus ventas pendientes: siguen siendo suyos, pero no los
 * puede volver a vender (FR-016). Siempre {@code 0 ≤ committed ≤ quantity}.</p>
 */
@Getter
public class Holding {

    private final Long id;
    private final long ownerId;
    private final long playerId;
    private CostBasis basis;
    private int committed;

    private Holding(Long id, long ownerId, long playerId, CostBasis basis, int committed) {
        requireValidCommitted(committed, basis);
        this.id = id;
        this.ownerId = ownerId;
        this.playerId = playerId;
        this.basis = basis;
        this.committed = committed;
    }

    /**
     * La tenencia de quien compra un jugador por primera vez.
     */
    public static Holding empty(long ownerId, long playerId) {
        return new Holding(null, ownerId, playerId, CostBasis.EMPTY, 0);
    }

    /**
     * Reconstruye el estado leído de la base. La usa únicamente {@code HoldingMapper}.
     */
    public static Holding reconstitute(Long id, long ownerId, long playerId, CostBasis basis, int committed) {
        return new Holding(id, ownerId, playerId, basis, committed);
    }

    public int getQuantity() {
        return basis.quantity();
    }

    public int freeTokens() {
        return basis.quantity() - committed;
    }

    /**
     * Compromete los tokens de una venta nueva.
     */
    public void commit(int tokens) {
        requirePositive(tokens);
        if (tokens > freeTokens()) {
            throw new InsufficientTokensException(freeTokens());
        }
        committed += tokens;
    }

    /**
     * Devuelve tokens comprometidos al cancelar una venta.
     */
    public void releaseCommitted(int tokens) {
        requirePositive(tokens);
        requireValidCommitted(committed - tokens, basis);
        committed -= tokens;
    }

    public void buy(int tokens, BigDecimal paid) {
        basis = basis.acquire(tokens, paid);
    }

    /**
     * Vende tokens comprometidos en una venta: bajan la tenencia y los comprometidos.
     *
     * @return la ganancia o pérdida realizada (FR-029)
     */
    public BigDecimal sell(int tokens, BigDecimal received) {
        if (tokens > committed) {
            throw new MarketInvariantException("No se pueden vender " + tokens + " tokens con " + committed
                    + " comprometidos.");
        }
        Disposal disposal = basis.dispose(tokens, received);
        basis = disposal.basis();
        committed -= tokens;
        return disposal.realizedProfit();
    }

    private static void requirePositive(int tokens) {
        if (tokens <= 0) {
            throw new MarketInvariantException("La cantidad de tokens tiene que ser mayor que 0: " + tokens + ".");
        }
    }

    private static void requireValidCommitted(int committed, CostBasis basis) {
        if (committed < 0 || committed > basis.quantity()) {
            throw new MarketInvariantException("Una tenencia de " + basis.quantity() + " tokens no puede tener "
                    + committed + " comprometidos.");
        }
    }
}
