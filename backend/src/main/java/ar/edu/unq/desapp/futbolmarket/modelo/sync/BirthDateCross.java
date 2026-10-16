package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Un jugador de WhoScored cruzado por la fecha de nacimiento de su página (FR-019d): el nombre que
 * informó WhoScored, el jugador del catálogo, ya con su id de WhoScored, y la fecha. Va al registro
 * para poder revisar cada cruce (research D14).
 */
public record BirthDateCross(String reportedName, Player player, LocalDate birthDate) {
}
