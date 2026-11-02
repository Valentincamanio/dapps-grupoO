package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.BoostWeekStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.BoostedWeek;

/**
 * Los potenciados de una semana: del lunes {@code weekStart} al domingo {@code weekEnd}, en hora de
 * Argentina, y {@code endsAt}, el instante del cierre. {@code players} viene vacía si la semana no
 * tiene potenciados.
 */
public record BoostedWeekResponse(LocalDate weekStart, LocalDate weekEnd, Instant endsAt, BoostWeekStatus status,
                                  List<BoostedPlayerResponse> players) {

    private static final int DAYS_TO_SUNDAY = 6;

    public static BoostedWeekResponse from(BoostedWeek week) {
        return new BoostedWeekResponse(week.week().monday(), week.week().monday().plusDays(DAYS_TO_SUNDAY),
                week.week().end(), week.status(), week.players().stream().map(BoostedPlayerResponse::from).toList());
    }
}
