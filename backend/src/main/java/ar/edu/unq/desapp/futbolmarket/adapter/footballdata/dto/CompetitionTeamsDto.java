package ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta de {@code GET /competitions/{code}/teams}: la temporada en curso y los equipos con su
 * plantel. Como todos los DTO de la fuente, declara solo los campos que se usan, ignora el resto y
 * nunca sale del adapter (research D4).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitionTeamsDto(SeasonDto season, List<TeamDto> teams) {
}
