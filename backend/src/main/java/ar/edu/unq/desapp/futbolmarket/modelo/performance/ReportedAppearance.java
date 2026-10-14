package ar.edu.unq.desapp.futbolmarket.modelo.performance;

import java.math.BigDecimal;

/**
 * Un jugador en la planilla de un partido de WhoScored, titular o suplente.
 *
 * <p>{@code enteredAt} y {@code leftAt} son minutos expandidos (research D9). {@code sourceCounts}
 * trae las métricas que salen de las estadísticas de la página, ya sumadas; los goles, las
 * asistencias y las tarjetas salen de los eventos del partido. {@code age} es la edad que muestra
 * la página, para desempatar homónimos al cruzar.</p>
 */
public record ReportedAppearance(String whoscoredId, String name, Integer age, Side side, boolean starter,
                                 Integer enteredAt, Integer leftAt, MetricCounts sourceCounts, BigDecimal rating,
                                 boolean manOfTheMatch) {
    public ReportedAppearance {
        sourceCounts = sourceCounts == null ? MetricCounts.empty() : sourceCounts;
    }

    /**
     * Un suplente que no entró no tiene estadísticas, aunque haya recibido una tarjeta (FR-004).
     */
    public boolean entered() {
        return starter || enteredAt != null;
    }
}
