package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;

/**
 * Lo que dejó una semana cotizada: cuántas cotizaciones de cada tipo se guardaron.
 */
public record WeekResult(QuoteWeek week, int initial, int calculated, int requoted) {
    public WeekResult {
        if (week == null) {
            throw new QuoteInvariantException("La semana del resultado es obligatoria.");
        }
        if (initial < 0 || calculated < 0 || requoted < 0) {
            throw new QuoteInvariantException("Las cantidades de una semana no pueden ser negativas.");
        }
    }

    /**
     * Cuenta por tipo las cotizaciones guardadas de esa semana.
     */
    public static WeekResult of(QuoteWeek week, List<Quote> quotes) {
        return new WeekResult(week, count(quotes, QuoteType.INITIAL), count(quotes, QuoteType.CALCULATED),
                count(quotes, QuoteType.REQUOTED));
    }

    public int total() {
        return initial + calculated + requoted;
    }

    private static int count(List<Quote> quotes, QuoteType type) {
        return Math.toIntExact(quotes.stream().filter(quote -> quote.type() == type).count());
    }
}
