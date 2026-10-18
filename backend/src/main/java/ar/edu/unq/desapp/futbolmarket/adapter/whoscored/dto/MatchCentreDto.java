package ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * El objeto {@code matchCentreData} de la página de un partido. {@code statusCode} 6 es terminado y
 * {@code expandedMaxMinute} es el último minuto, con lo agregado en los dos tiempos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchCentreDto(Integer statusCode, Integer expandedMaxMinute, MatchSideDto home, MatchSideDto away,
                             List<MatchEventDto> events) {
}
