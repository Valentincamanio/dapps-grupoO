package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.LocalDate;

/**
 * Una fila de la página de fixtures de un equipo de WhoScored, ya filtrada por el torneo de la
 * liga. La fecha es la del sitio, en la hora de Londres: puede diferir en un día de la fecha UTC
 * del partido (research D12 y D13).
 */
public record ReportedFixture(String whoscoredId, LocalDate date, String homeTeamWhoscoredId, String homeTeamName,
                              String awayTeamWhoscoredId, String awayTeamName, boolean finished) {
}
