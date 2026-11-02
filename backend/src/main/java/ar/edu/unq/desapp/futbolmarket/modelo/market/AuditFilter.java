package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.time.Instant;
import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteDay;

/**
 * Los filtros de la auditoría (FR-053): tipo de hecho, usuario (como autor o como cualquiera de las
 * partes), jugador y un rango de días en hora de Argentina con los dos extremos incluidos. Un filtro
 * nulo no filtra. Las fechas siguen las reglas de {@link TradeFilter}.
 */
public record AuditFilter(AuditEventType eventType, Long userId, Long playerId, LocalDate from, LocalDate to) {

    public AuditFilter {
        if (from != null && to != null) {
            QuoteDay.requireOrdered(from, to);
        }
    }

    /**
     * Las fechas llegan como texto ({@code AAAA-MM-DD}) y se parsean acá, así el error repite el
     * valor no admitido (400). Solo se parsean las que vienen.
     */
    public static AuditFilter of(AuditEventType eventType, Long userId, Long playerId, String from, String to) {
        return new AuditFilter(eventType, userId, playerId, from == null ? null : QuoteDay.parse(from),
                to == null ? null : QuoteDay.parse(to));
    }

    public static AuditFilter none() {
        return new AuditFilter(null, null, null, null, null);
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
