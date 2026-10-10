package ar.edu.unq.desapp.futbolmarket.adapter.footballdata.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeasonDto(Long id, LocalDate startDate, LocalDate endDate, Integer currentMatchday) {
}
