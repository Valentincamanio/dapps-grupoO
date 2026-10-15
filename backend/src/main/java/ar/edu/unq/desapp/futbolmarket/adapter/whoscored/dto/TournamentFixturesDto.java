package ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * El JSON {@code tournamentfixtures} de la página de una liga: los partidos de la fecha en curso.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TournamentFixturesDto(List<TournamentDto> tournaments) {
}
