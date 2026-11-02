package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Los números de los mensajes y de la auditoría, escritos como en el spec: coma decimal y sin
 * separador de miles ("1016,78", "1,0300"). Se arma a mano y no con un {@code NumberFormat} de
 * Argentina porque ese agrega el punto de los miles y depende de los datos de locale del JDK.
 */
public final class MarketFormat {
    private static final char DECIMAL_POINT = '.';
    private static final char DECIMAL_COMMA = ',';

    private MarketFormat() {
    }

    public static String credits(BigDecimal amount) {
        return withComma(amount.setScale(MarketMath.CREDIT_SCALE, RoundingMode.HALF_UP));
    }

    public static String price(BigDecimal tokenPrice) {
        return withComma(tokenPrice.setScale(MarketMath.PRICE_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * "1 token" o "N tokens".
     */
    public static String tokens(int quantity) {
        return quantity == 1 ? "1 token" : quantity + " tokens";
    }

    private static String withComma(BigDecimal value) {
        return value.toPlainString().replace(DECIMAL_POINT, DECIMAL_COMMA);
    }
}
