package ar.edu.unq.desapp.futbolmarket.modelo.strategy;

import java.util.Arrays;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.exception.InvalidRequoteTimingException;

/**
 * Cuándo rige un cambio de la estrategia activa (research D14): recotizar en el acto la última
 * semana cerrada, o solo cambiar la activa y que la use el próximo cierre semanal. Los valores de
 * la API ({@code AHORA} y {@code PROXIMA_SEMANA}) los fijó el equipo.
 */
public enum RequoteTiming {
    NOW("AHORA"),
    NEXT_WEEK("PROXIMA_SEMANA");

    private final String wireValue;

    RequoteTiming(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    /**
     * La opción que mandó el cliente. Si no mandó ninguna, rige desde la semana siguiente
     * (FR-034); un valor que no es ninguno de los dos se rechaza con 400.
     */
    public static RequoteTiming fromWire(String value) {
        if (value == null) {
            return NEXT_WEEK;
        }
        return Arrays.stream(values())
                .filter(timing -> timing.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new InvalidRequoteTimingException(value));
    }
}
