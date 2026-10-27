package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;

/**
 * Una semana de cotización: del lunes 00:00 al lunes 00:00 siguiente, en hora de Argentina
 * (research D3, FR-007). Se identifica por su lunes.
 *
 * <p>La zona es una definición del dominio, no una propiedad: el cron del recálculo sí es
 * configurable, la semana no.</p>
 */
public record QuoteWeek(LocalDate monday) implements Comparable<QuoteWeek> {
    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    public QuoteWeek {
        if (monday == null) {
            throw new QuoteInvariantException("El lunes de la semana es obligatorio.");
        }
        if (!DayOfWeek.MONDAY.equals(monday.getDayOfWeek())) {
            throw new QuoteInvariantException("La semana tiene que empezar un lunes y " + monday + " no lo es.");
        }
    }

    /**
     * La semana en la que cae el instante, en hora de Argentina: un partido del domingo 23:59 es de
     * esa semana y uno del lunes 00:00, de la siguiente.
     */
    public static QuoteWeek containing(Instant instant) {
        LocalDate day = LocalDate.ofInstant(instant, ZONE);
        return new QuoteWeek(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    /**
     * La última semana cerrada a ese instante: la anterior a la que lo contiene. Un lunes a las
     * 00:00 exactas, la semana que termina en ese instante ya está cerrada.
     */
    public static QuoteWeek lastClosedAt(Instant instant) {
        return containing(instant).previous();
    }

    public Instant start() {
        return monday.atStartOfDay(ZONE).toInstant();
    }

    public Instant end() {
        return next().start();
    }

    public QuoteWeek next() {
        return new QuoteWeek(monday.plusWeeks(1));
    }

    public QuoteWeek previous() {
        return new QuoteWeek(monday.minusWeeks(1));
    }

    /**
     * Si la semana terminó a ese instante: el cierre es el lunes 00:00 siguiente, incluido.
     */
    public boolean isClosedAt(Instant instant) {
        return !instant.isBefore(end());
    }

    @Override
    public int compareTo(QuoteWeek other) {
        return monday.compareTo(other.monday);
    }
}
