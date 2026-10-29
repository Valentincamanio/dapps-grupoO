package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;

/**
 * Los totales de un portfolio (data-model 2.11, FR-038). Lo invertido, el valor y la ganancia son
 * la suma de las posiciones; las que no tienen costo (las del superusuario) no suman invertido ni
 * ganancia. El patrimonio es el saldo total más el valor de las posiciones.
 *
 * @param availableBalance el saldo libre; nulo en el portfolio a una fecha (FR-040)
 * @param reservedBalance  el saldo reservado; nulo en el portfolio a una fecha
 * @param balance          el saldo total
 * @param realizedProfit   la ganancia realizada en ventas; nula en el del superusuario
 */
public record PortfolioTotals(BigDecimal availableBalance, BigDecimal reservedBalance, BigDecimal balance,
                              BigDecimal invested, BigDecimal value, BigDecimal profit, BigDecimal realizedProfit,
                              BigDecimal equity) {

    /**
     * Los totales del portfolio actual, con el saldo libre y el reservado de la cuenta.
     */
    public static PortfolioTotals current(AppUser account, List<PortfolioPosition> positions,
                                          BigDecimal realizedProfit) {
        BigDecimal value = sum(positions, PortfolioPosition::value);
        return new PortfolioTotals(account.availableBalance(), account.getReserved(), account.getBalance(),
                sum(positions, PortfolioPosition::invested), value, sum(positions, PortfolioPosition::profit),
                realizedProfit, account.getBalance().add(value));
    }

    private static BigDecimal sum(List<PortfolioPosition> positions, Function<PortfolioPosition, BigDecimal> field) {
        return positions.stream()
                .map(field)
                .filter(Objects::nonNull)
                .reduce(MarketMath.ZERO_CREDITS, BigDecimal::add);
    }
}
