package ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchDto(Long id, SeasonDto season, Instant utcDate, String status, Integer matchday,
                       MatchTeamDto homeTeam, MatchTeamDto awayTeam, ScoreDto score) {
}
