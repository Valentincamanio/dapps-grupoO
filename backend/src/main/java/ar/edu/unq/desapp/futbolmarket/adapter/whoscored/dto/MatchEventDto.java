package ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Un evento del partido. Se usan los goles ({@code isGoal}) y las tarjetas ({@code type} 17, con su
 * {@code cardType}). {@code eventId} es único por equipo, no por partido.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchEventDto(Long eventId, Integer expandedMinute, Long teamId, Long playerId, CodeDto type,
                            @JsonProperty("isGoal") Boolean isGoal,
                            @JsonProperty("isOwnGoal") Boolean isOwnGoal,
                            List<QualifierDto> qualifiers, Long relatedPlayerId, CodeDto cardType) {
}
