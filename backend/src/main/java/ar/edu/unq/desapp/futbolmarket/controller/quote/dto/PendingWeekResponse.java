package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.time.LocalDate;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingReason;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PendingWeek;

/**
 * Una semana que quedó sin cotizar, con los campos del esquema {@code PendingWeek} del contrato.
 */
public record PendingWeekResponse(LocalDate weekStart, PendingReason reason) {

    public static PendingWeekResponse from(PendingWeek pending) {
        return new PendingWeekResponse(pending.week().monday(), pending.reason());
    }
}
