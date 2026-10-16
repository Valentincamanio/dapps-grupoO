package ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto;

import java.time.LocalDate;

/**
 * Lo que se lee de la página de un jugador: su fecha de nacimiento y su nombre completo, cada uno
 * {@code null} si la página no lo trae (research D28).
 */
public record PlayerProfileDto(LocalDate birthDate, String fullName) {
}
