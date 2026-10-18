package ar.edu.unq.desapp.futbolmarket.modelo.performance;

/**
 * Un gol tal como lo informa WhoScored, con los jugadores por su id de WhoScored.
 * {@code assisterWhoscoredId} es {@code null} si el gol no trae asistencia (research D10).
 */
public record ReportedGoal(String scorerWhoscoredId, boolean ownGoal, String assisterWhoscoredId) {
}
