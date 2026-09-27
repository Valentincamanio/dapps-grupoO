package ar.edu.unq.desapp.futbolmarket.catalog.modelo;

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
