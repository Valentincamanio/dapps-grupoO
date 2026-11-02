package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;
import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.market.BookOutcome;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;

/**
 * El resultado de una compra o una venta (esquema {@code OrderExecution}): la orden creada, las
 * operaciones que se ejecutaron en el acto, lo ejecutado y los saldos de la cuenta después del
 * pedido.
 */
public record OrderExecutionResponse(UserOrderResponse order, List<TradeSummaryResponse> trades,
                                     int executedQuantity, BigDecimal executedTotal, BigDecimal balance,
                                     BigDecimal availableBalance, BigDecimal reservedBalance) {

    public static OrderExecutionResponse from(BookOutcome outcome) {
        AppUser account = outcome.account();
        List<TradeSummaryResponse> trades = outcome.trades().stream()
                .map(trade -> TradeSummaryResponse.from(trade, account.getId(), outcome.quote()))
                .toList();
        return new OrderExecutionResponse(UserOrderResponse.from(outcome.order(), outcome.player()), trades,
                outcome.executedQuantity(), outcome.executedTotal(), account.getBalance(), account.availableBalance(),
                account.getReserved());
    }
}
