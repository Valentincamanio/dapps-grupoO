package ar.edu.unq.desapp.futbolmarket.modelo.player;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;

public record PlayerFilter(League league, String team, Position position) {
    public PlayerFilter {
        team = normalizeTeam(team);
    }

    public boolean hasCriteria() {
        return league != null || team != null || position != null;
    }

    private static String normalizeTeam(String team) {
        if (team == null) {
            return null;
        }
        var normalizedTeam = team.trim().replaceAll("\\s+", " ");
        if (normalizedTeam.isEmpty()) {
            throw new CatalogInvariantException("El equipo del filtro no puede estar vacío.");
        }
        return normalizedTeam;
    }
}
