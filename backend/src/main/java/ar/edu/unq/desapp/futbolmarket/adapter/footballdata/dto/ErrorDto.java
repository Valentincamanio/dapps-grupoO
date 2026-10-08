package ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cuerpo de una respuesta de error. El {@code message} solo se registra; no va al informe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorDto(String message, Integer errorCode) {
}
