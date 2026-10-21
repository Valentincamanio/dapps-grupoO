package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Los equipos y partidos del catálogo, de cualquier temporada, que nombra el snapshot de una liga.
 * Distinguen lo que no está en el catálogo, que se cuenta, de lo que es de otra temporada o de un
 * equipo que dejó la liga, que se ignora sin contarlo (research D20).
 */
public record CatalogReferences(List<Team> teams, List<Match> matches) {
    public CatalogReferences {
        teams = List.copyOf(teams);
        matches = List.copyOf(matches);
    }
}
