package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.auth.CredentialPolicy;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;

/**
 * La aritmética del mercado (research D4): todo con {@link BigDecimal}, {@code HALF_UP} y un solo
 * redondeo por resultado, así los ejemplos del spec se reproducen sin diferencias.
 *
 * <p>Las escalas no se repiten: los créditos usan la del saldo de la cuenta, y el precio por token
 * y los porcentajes, las de la cotización, que es de donde salen.</p>
 */
public final class MarketMath {
    public static final int CREDIT_SCALE = CredentialPolicy.BALANCE_SCALE;
    public static final int PRICE_SCALE = Quote.TOKEN_PRICE_SCALE;
    public static final int PERCENT_SCALE = Quote.PERCENT_SCALE;
    public static final int TOKENS_PER_PLAYER = 100;
    public static final int MAX_ORDER_QUANTITY = 100;
    public static final BigDecimal INITIAL_TOKEN_PRICE = new BigDecimal("1.0000");
    public static final BigDecimal ZERO_CREDITS = BigDecimal.ZERO.setScale(CREDIT_SCALE);

    private static final BigDecimal HUNDRED_PERCENT = new BigDecimal("100");

    private MarketMath() {
    }

    /**
     * Lo que se paga por esa cantidad de tokens: el producto redondeado una sola vez a centavos
     * (FR-003). El comprador paga y el vendedor cobra exactamente esto.
     */
    public static BigDecimal total(int quantity, BigDecimal tokenPrice) {
        return tokenPrice.multiply(BigDecimal.valueOf(quantity)).setScale(CREDIT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * La mayor cantidad, hasta {@code limit}, cuyo total no supera lo disponible (FR-027). Parte del
     * cociente entero y resta uno si el redondeo del total se pasa, que es lo único que puede
     * fallar: así no recorre cantidades.
     */
    public static int maxAffordable(BigDecimal available, BigDecimal tokenPrice, int limit) {
        if (available.signum() <= 0) {
            return 0;
        }
        int quantity = available.divide(tokenPrice, 0, RoundingMode.FLOOR)
                .min(BigDecimal.valueOf(limit))
                .intValueExact();
        if (total(quantity, tokenPrice).compareTo(available) > 0) {
            quantity--;
        }
        return quantity;
    }

    /**
     * El precio promedio de compra con la escala de un precio por token. Sin tokens no hay promedio:
     * eso lo resuelve quien llama.
     */
    public static BigDecimal averagePrice(BigDecimal cost, int quantity) {
        return cost.divide(BigDecimal.valueOf(quantity), PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Qué porcentaje de {@code whole} es {@code part}. Vacío si {@code whole} es 0: una rentabilidad
     * sobre nada invertido no se informa.
     */
    public static Optional<BigDecimal> percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(part.multiply(HUNDRED_PERCENT).divide(whole, PERCENT_SCALE, RoundingMode.HALF_UP));
    }
}
