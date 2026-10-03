package ar.edu.unq.desapp.futbolmarket.controller.player.dto;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;

public record PlayerResponse(Long id, String name, Position position, String team, League league) {
}
