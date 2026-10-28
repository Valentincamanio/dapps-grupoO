package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market;

import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketMath;

/**
 * Los tipos de columna que comparten las cuatro tablas del mercado (research D17): créditos como
 * {@code app_user.balance}, precios por token con cuatro decimales y los enums como texto de largo
 * fijo, traducidos por los mappers.
 */
final class MarketColumns {
    static final int CREDIT_PRECISION = 19;
    static final int CREDIT_SCALE = MarketMath.CREDIT_SCALE;
    static final int PRICE_PRECISION = 10;
    static final int PRICE_SCALE = MarketMath.PRICE_SCALE;
    static final int SIDE_LENGTH = 4;
    static final int OWNER_TYPE_LENGTH = 5;
    static final int STATUS_LENGTH = 10;
    static final int CANCEL_REASON_LENGTH = 20;
    static final int EVENT_TYPE_LENGTH = 20;
    static final int ACTOR_TYPE_LENGTH = 10;

    private MarketColumns() {
    }
}
