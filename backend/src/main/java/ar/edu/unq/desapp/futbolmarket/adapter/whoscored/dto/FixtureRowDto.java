package ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Una fila de {@code fixtureMatches}, con las posiciones que se usan del array
 * (contracts/whoscored-pages.md). {@code date} es {@code dd-MM-yy} en la zona del sitio y
 * {@code status} queda en {@code null} si el partido no se jugó.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FixtureRowDto(Long id, String date, Long homeTeamId, String homeTeamName, Long awayTeamId,
                            String awayTeamName, String status, Integer tournamentId) {
}
