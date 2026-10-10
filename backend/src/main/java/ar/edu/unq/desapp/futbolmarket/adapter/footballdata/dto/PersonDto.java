package ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Un jugador del plantel. La posición se lee como texto: un valor nuevo de la fuente no rompe la
 * lectura de la liga (research D4).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonDto(Long id, String name, String position, LocalDate dateOfBirth, String nationality) {
}
