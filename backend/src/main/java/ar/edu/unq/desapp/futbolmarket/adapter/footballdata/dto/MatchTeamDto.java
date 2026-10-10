package ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchTeamDto(Long id) {
}
