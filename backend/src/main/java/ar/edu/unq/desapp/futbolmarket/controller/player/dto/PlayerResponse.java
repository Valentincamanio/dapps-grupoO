package ar.edu.unq.desapp.futbolmarket.controller.player.dto;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;

/**
 * Un jugador del catálogo. {@code dateOfBirth} y {@code nationality} salen en {@code null} si la
 * fuente no los informa, y {@code active} es siempre {@code true} en el listado (FR-046 y research
 * D18).
 */
public record PlayerResponse(Long id, String name, Position position, String team, League league,
                             LocalDate dateOfBirth, String nationality, String teamCrest, boolean active) {
}
