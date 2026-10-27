package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * Un jugador de WhoScored cruzado por el nombre completo y el año de nacimiento de su página
 * (FR-019d, paso 3): el nombre que informó WhoScored, el nombre completo, y el jugador del
 * catálogo, ya con su id de WhoScored, cuyo nombre está dentro del completo. Va al registro para
 * poder revisar cada cruce (research D14).
 */
public record FullNameCross(String reportedName, String fullName, Player player) {
}
