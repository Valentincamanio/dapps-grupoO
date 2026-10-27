package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.FutureQuoteDateException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidDateRangeException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidQuoteDateException;

/**
 * Los días de las consultas de cotizaciones, en hora de Argentina como las semanas
 * ({@link QuoteWeek#ZONE}).
 *
 * <p>Las fechas llegan como texto y se parsean acá, así el mensaje de error repite el valor no
 * admitido (research D20). Un día va de su 00:00 al 00:00 del día siguiente: "la cotización que
 * regía al final del día" es la última con vigencia anterior al comienzo del día siguiente, y un
 * rango incluye sus dos extremos completos.</p>
 */
public final class QuoteDay {
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private QuoteDay() {
    }

    /**
     * El día escrito como {@code AAAA-MM-DD}. Rechaza un día inexistente ({@code 2026-02-30}) y la
     * falta del valor.
     */
    public static LocalDate parse(String value) {
        if (value == null) {
            throw new InvalidQuoteDateException(null);
        }
        try {
            return LocalDate.parse(value, FORMAT);
        } catch (DateTimeParseException e) {
            throw new InvalidQuoteDateException(value);
        }
    }

    /**
     * El día de hoy en hora de Argentina a ese instante.
     */
    public static LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, QuoteWeek.ZONE);
    }

    public static LocalDate requireNotFuture(LocalDate day, LocalDate today) {
        if (day.isAfter(today)) {
            throw new FutureQuoteDateException(day);
        }
        return day;
    }

    /**
     * Un rango con los dos extremos iguales es válido: es un solo día.
     */
    public static void requireOrdered(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidDateRangeException(from, to);
        }
    }

    /**
     * El 00:00 del día en hora de Argentina.
     */
    public static Instant startOf(LocalDate day) {
        return day.atStartOfDay(QuoteWeek.ZONE).toInstant();
    }

    /**
     * El final del día, excluido: el 00:00 del día siguiente en hora de Argentina.
     */
    public static Instant endOf(LocalDate day) {
        return startOf(day.plusDays(1));
    }
}
