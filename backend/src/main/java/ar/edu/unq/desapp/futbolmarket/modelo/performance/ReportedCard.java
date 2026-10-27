package ar.edu.unq.desapp.futbolmarket.modelo.performance;

/**
 * Una tarjeta tal como la informa WhoScored, con el jugador por su id de WhoScored y el minuto
 * expandido, que ya suma lo agregado en el primer tiempo (research D9).
 */
public record ReportedCard(String playerWhoscoredId, CardKind kind, int minute) {
}
