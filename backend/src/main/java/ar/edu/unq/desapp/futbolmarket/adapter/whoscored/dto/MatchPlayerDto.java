package ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Un jugador de la planilla. Cada métrica de {@code stats}, también {@code ratings}, es un mapa
 * minuto → valor con solo los minutos que tienen valor. {@code isFirstEleven} falta en los
 * suplentes y {@code subbedOutExpandedMinute}, en un expulsado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchPlayerDto(Long playerId, String name, Integer age,
                             @JsonProperty("isFirstEleven") Boolean isFirstEleven,
                             @JsonProperty("isManOfTheMatch") Boolean isManOfTheMatch,
                             Integer subbedInExpandedMinute, Integer subbedOutExpandedMinute,
                             Map<String, Map<String, BigDecimal>> stats) {
}
