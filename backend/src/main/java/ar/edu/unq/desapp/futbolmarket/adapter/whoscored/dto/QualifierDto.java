package ar.edu.unq.desapp.futbolmarket.adapter.whoscored.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QualifierDto(CodeDto type) {
}
