package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.market.BookOutcome;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;

/**
 * El resultado de cancelar una orden (esquema {@code OrderCancellation}): la orden cancelada y los
 * saldos de la cuenta después.
 */
public record OrderCancellationResponse(UserOrderResponse order, BigDecimal balance, BigDecimal availableBalance,
                                        BigDecimal reservedBalance) {

    public static OrderCancellationResponse from(BookOutcome outcome) {
        AppUser account = outcome.account();
        return new OrderCancellationResponse(UserOrderResponse.from(outcome.order(), outcome.player()),
                account.getBalance(), account.availableBalance(), account.getReserved());
    }
}
