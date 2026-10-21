package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.SimulatedQuote;

/**
 * Una fila de la simulación: el jugador, su cotización actual ({@code null} si no tiene), la
 * simulada y la diferencia, con los campos del esquema {@code SimulationRow} del contrato.
 */
public record SimulationRowResponse(long playerId, String name, String team, League league, Position position,
                                    BigDecimal currentValue, BigDecimal simulatedValue, BigDecimal difference) {

    public static SimulationRowResponse from(SimulatedQuote row) {
        Player player = row.player();
        return new SimulationRowResponse(player.id(), player.name(), player.team().name(), player.league(),
                player.position(), row.currentValue(), row.simulatedValue(), row.difference());
    }
}
