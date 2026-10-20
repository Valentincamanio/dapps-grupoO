package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankedQuote;

/**
 * Una fila del ranking: los datos del jugador que muestra el catálogo y su cotización vigente.
 * {@code teamCrest} sale en {@code null} si la fuente no lo informa.
 */
public record RankingRowResponse(long playerId, String name, Position position, String team, League league,
                                 String teamCrest, QuoteResponse quote) {

    public static RankingRowResponse from(RankedQuote row) {
        Player player = row.player();
        return new RankingRowResponse(player.id(), player.name(), player.position(), player.team().name(),
                player.league(), player.team().crest(), QuoteResponse.from(row.quote()));
    }
}
