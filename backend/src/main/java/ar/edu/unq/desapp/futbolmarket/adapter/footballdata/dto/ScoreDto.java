package ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreDto(String winner, ScoreLineDto fullTime, ScoreLineDto halfTime) {
}
