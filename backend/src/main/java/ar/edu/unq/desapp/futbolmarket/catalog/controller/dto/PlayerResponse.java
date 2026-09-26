package ar.edu.unq.desapp.futbolmarket.catalog.controller.dto;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.League;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Position;

public record PlayerResponse(Long id, String name, Position position, String team, League league) {
}
