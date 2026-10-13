package ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Goles de local y de visitante. Los dos vienen en {@code null} si el partido no se jugó.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreLineDto(Integer home, Integer away) {
}
