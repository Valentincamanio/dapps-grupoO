package ar.edu.unq.desapp.futbolmarket.modelo.league.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Se lanza cuando se pide una liga que no es una de las cinco. Responde 400 y el mensaje repite el
 * valor recibido, que no es un dato sensible (FR-030).
 */
public class UnsupportedLeagueException extends BadRequestException {

    public UnsupportedLeagueException(String value) {
        super("La liga '%s' no es una de las admitidas: %s.".formatted(value, supportedLeagues()));
    }

    /**
     * Se arma desde el enum, así una liga nueva aparece sola en el mensaje.
     */
    private static String supportedLeagues() {
        return Arrays.stream(League.values()).map(League::name).collect(Collectors.joining(", "));
    }
}
