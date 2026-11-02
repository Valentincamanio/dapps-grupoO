package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.math.RoundingMode;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;

/**
 * Lo que costaron los tokens de una tenencia (research D12): los tokens que tiene y, desde la
 * última vez que llegó a 0, cuántos compró y cuánto pagó por ellos.
 *
 * <p>El precio promedio se deriva de esos dos acumuladores en vez de guardarse: guardado y
 * redondeado a cuatro decimales, cada compra nueva arrastraría el error del redondeo anterior.
 * Lo comparten la tenencia actual y la reconstrucción a una fecha, así las dos no pueden calcular
 * distinto.</p>
 */
public record CostBasis(int quantity, int acquiredQuantity, BigDecimal acquiredCost) {

    public static final CostBasis EMPTY = new CostBasis(0, 0, MarketMath.ZERO_CREDITS);

    private static final BigDecimal NO_AVERAGE = BigDecimal.ZERO.setScale(MarketMath.PRICE_SCALE);

    public CostBasis {
        if (quantity < 0 || acquiredQuantity < quantity) {
            throw new MarketInvariantException("Una tenencia no puede tener " + quantity + " tokens habiendo comprado "
                    + acquiredQuantity + ".");
        }
        if (acquiredCost == null || acquiredCost.signum() < 0) {
            throw new MarketInvariantException("Lo pagado por una tenencia no puede ser negativo: " + acquiredCost + ".");
        }
    }

    public CostBasis acquire(int tokens, BigDecimal paid) {
        requirePositive(tokens);
        return new CostBasis(quantity + tokens, acquiredQuantity + tokens, acquiredCost.add(paid));
    }

    /**
     * Vende tokens al precio promedio de compra, que no cambia (FR-037). La ganancia realizada se
     * redondea una sola vez (FR-029). Si la tenencia llega a 0, los acumuladores vuelven a cero y
     * la próxima compra arranca un promedio nuevo (US2.4).
     */
    public Disposal dispose(int tokens, BigDecimal received) {
        requirePositive(tokens);
        if (tokens > quantity) {
            throw new MarketInvariantException("No se pueden vender " + tokens + " tokens de una tenencia de "
                    + quantity + ".");
        }
        BigDecimal realized = received.subtract(averagePrice().multiply(BigDecimal.valueOf(tokens)))
                .setScale(MarketMath.CREDIT_SCALE, RoundingMode.HALF_UP);
        int remaining = quantity - tokens;
        CostBasis basis = remaining == 0 ? EMPTY : new CostBasis(remaining, acquiredQuantity, acquiredCost);
        return new Disposal(basis, realized);
    }

    /**
     * Sin compras no hay promedio: vale 0.
     */
    public BigDecimal averagePrice() {
        if (acquiredQuantity == 0) {
            return NO_AVERAGE;
        }
        return MarketMath.averagePrice(acquiredCost, acquiredQuantity);
    }

    /**
     * Lo invertido en los tokens que todavía tiene.
     */
    public BigDecimal invested() {
        return MarketMath.total(quantity, averagePrice());
    }

    private static void requirePositive(int tokens) {
        if (tokens <= 0) {
            throw new MarketInvariantException("La cantidad de tokens tiene que ser mayor que 0: " + tokens + ".");
        }
    }
}
