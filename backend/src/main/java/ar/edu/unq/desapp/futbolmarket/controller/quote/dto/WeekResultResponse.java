package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekResult;

/**
 * Una semana cotizada, con los campos del esquema {@code WeekResult} del contrato.
 */
public record WeekResultResponse(LocalDate weekStart, int initial, int calculated, int requoted) {

    public static WeekResultResponse from(WeekResult result) {
        return new WeekResultResponse(result.week().monday(), result.initial(), result.calculated(),
                result.requoted());
    }
}
