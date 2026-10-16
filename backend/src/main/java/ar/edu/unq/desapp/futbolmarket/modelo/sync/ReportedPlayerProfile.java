package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.LocalDate;

/**
 * Lo que trae la página de un jugador de WhoScored para resolver su cruce (FR-019d; research D28):
 * su fecha de nacimiento y su nombre completo. Cualquiera de los dos es nulo si la página no lo
 * trae.
 */
public record ReportedPlayerProfile(LocalDate birthDate, String fullName) {
}
