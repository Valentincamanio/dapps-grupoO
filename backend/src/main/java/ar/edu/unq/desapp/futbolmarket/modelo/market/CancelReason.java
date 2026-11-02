package ar.edu.unq.desapp.futbolmarket.modelo.market;

/**
 * Por qué se canceló una orden: la pidió su dueño, o el saldo de una compra no alcanzó para
 * pagar lo que se le ofrecía (FR-027).
 */
public enum CancelReason {
    USER_REQUEST,
    INSUFFICIENT_CREDITS
}
