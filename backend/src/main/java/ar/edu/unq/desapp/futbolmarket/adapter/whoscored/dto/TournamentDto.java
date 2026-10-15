package ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TournamentDto(Integer tournamentId, List<TournamentMatchDto> matches) {
}
