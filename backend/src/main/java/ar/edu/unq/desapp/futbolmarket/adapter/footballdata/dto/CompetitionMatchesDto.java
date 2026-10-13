package ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta de {@code GET /competitions/{code}/matches}: todos los partidos de la temporada en curso.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitionMatchesDto(List<MatchDto> matches) {
}
