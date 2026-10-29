package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;

/**
 * Las posiciones de una cuenta y sus totales (data-model 2.11). Se calcula en cada pedido y no se
 * guarda. Las posiciones van por nombre del jugador y, a igual nombre, por id, así el orden no
 * depende de la consulta.
 *
 * @param date la fecha pedida; nula en el portfolio actual
 */
public record Portfolio(Long userId, LocalDate date, List<PortfolioPosition> positions, PortfolioTotals totals) {

    private static final Comparator<PortfolioPosition> BY_PLAYER =
            Comparator.comparing((PortfolioPosition position) -> position.player().name())
                    .thenComparing(position -> position.player().id());

    public Portfolio {
        positions = List.copyOf(positions);
    }

    /**
     * El portfolio actual de un usuario: cada tenencia con la cotización vigente de su jugador,
     * también la de un jugador inactivo (FR-039).
     *
     * @param quotesByPlayer la cotización vigente por id de jugador
     * @param realizedProfit la ganancia realizada acumulada en sus ventas
     */
    public static Portfolio current(AppUser account, List<HoldingView> holdings, Map<Long, Quote> quotesByPlayer,
                                    BigDecimal realizedProfit) {
        List<PortfolioPosition> positions = holdings.stream()
                .filter(view -> view.holding().getQuantity() > 0)
                .map(view -> PortfolioPosition.held(view.player(), view.holding().getBasis(),
                        view.holding().getCommitted(), requireQuote(quotesByPlayer, view.holding().getPlayerId())))
                .sorted(BY_PLAYER)
                .toList();
        return new Portfolio(account.getId(), null, positions,
                PortfolioTotals.current(account, positions, realizedProfit));
    }

    /**
     * El portfolio actual del superusuario: los tokens sin vender de cada orden inicial, sin datos
     * de costo ni ganancia realizada (research D7). Un jugador todavía sin cotización no se puede
     * valuar ni operar, así que no figura.
     */
    public static Portfolio currentOfSuperuser(AppUser account, List<OrderView> initialOrders,
                                               Map<Long, Quote> quotesByPlayer) {
        List<PortfolioPosition> positions = initialOrders.stream()
                .filter(view -> view.order().pending() > 0)
                .flatMap(view -> Stream.ofNullable(quotesByPlayer.get(view.order().getPlayerId()))
                        .map(quote -> PortfolioPosition.unsold(view.player(), view.order().pending(), quote)))
                .sorted(BY_PLAYER)
                .toList();
        return new Portfolio(account.getId(), null, positions, PortfolioTotals.current(account, positions, null));
    }

    /**
     * Para comprar hace falta una cotización, y las cotizaciones no se borran: una tenencia sin
     * ninguna es un error de los datos.
     */
    private static Quote requireQuote(Map<Long, Quote> quotesByPlayer, long playerId) {
        Quote quote = quotesByPlayer.get(playerId);
        if (quote == null) {
            throw new MarketInvariantException("La tenencia del jugador " + playerId + " no tiene cotización.");
        }
        return quote;
    }
}
