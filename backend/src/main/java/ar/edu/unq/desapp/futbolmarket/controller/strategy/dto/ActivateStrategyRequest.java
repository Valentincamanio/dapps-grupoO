package ar.edu.unq.desapp.futbolmarket.controller.strategy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.RequoteTiming;

/**
 * Cuerpo opcional de {@code POST /strategies/{id}/activate}. {@code requote} es {@code AHORA} o
 * {@code PROXIMA_SEMANA}; llega como texto para que {@link RequoteTiming#fromWire(String)} informe
 * el valor inválido. Sin cuerpo o sin la opción, rige desde la semana siguiente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivateStrategyRequest(String requote) {

    public RequoteTiming timing() {
        return RequoteTiming.fromWire(requote);
    }

    /**
     * La opción del cuerpo, que puede no venir.
     */
    public static RequoteTiming timingOf(ActivateStrategyRequest request) {
        return request == null ? RequoteTiming.fromWire(null) : request.timing();
    }
}
