package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.time.Instant;
import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteDay;

/**
 * Los filtros del historial de un usuario (FR-043): jugador, compra o venta desde su punto de vista,
 * y un rango de días en hora de Argentina con los dos extremos incluidos. Un filtro nulo no filtra.
 */
public record TradeFilter(Long playerId, OrderSide side, LocalDate from, LocalDate to) {

    public TradeFilter {
        if (from != null && to != null) {
            QuoteDay.requireOrdered(from, to);
        }
    }

    /**
     * Las fechas llegan como texto ({@code AAAA-MM-DD}) y se parsean acá, así el error repite el
     * valor no admitido (400). Solo se parsean las que vienen.
     */
    public static TradeFilter of(Long playerId, OrderSide side, String from, String to) {
        return new TradeFilter(playerId, side, from == null ? null : QuoteDay.parse(from),
                to == null ? null : QuoteDay.parse(to));
    }

    public static TradeFilter none() {
        return new TradeFilter(null, null, null, null);
    }

    /**
     * El comienzo del rango, incluido: el 00:00 del primer día. Nulo sin fecha de inicio.
     */
    public Instant fromInstant() {
        return from == null ? null : QuoteDay.startOf(from);
    }

    /**
     * El final del rango, excluido: el 00:00 del día siguiente al último. Nulo sin fecha de fin.
     */
    public Instant toExclusive() {
        return to == null ? null : QuoteDay.endOf(to);
    }
}
