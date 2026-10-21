package ar.edu.unq.desapp.futbolmarket.controller.strategy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cuerpo opcional de {@code POST /strategies/{id}/activate}. La opción {@code requote} se suma en
 * US5; hasta entonces el cambio rige desde la semana siguiente y cualquier propiedad se ignora.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivateStrategyRequest() {
}
