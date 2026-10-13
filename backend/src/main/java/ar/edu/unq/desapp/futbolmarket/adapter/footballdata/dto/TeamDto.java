package ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * No declara {@code shortName}: se guarda solo el nombre oficial (FR-008).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamDto(Long id, String name, String crest, List<PersonDto> squad) {
}
