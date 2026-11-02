package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.BETO;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.KANE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.NDIAYE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.ZIRKZEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.PlayerNotFoundException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.BoostWeekStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.BoostedWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerBoost;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekBoosts;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.BoostChangeConflictException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.ClosedBoostWeekException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidBoostException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.BoostRepository;

/**
 * Los potenciados con el reloj fijo el miércoles 04/11/2026, en la semana del 02/11.
 */
@ExtendWith(MockitoExtension.class)
class BoostServiceTest {
    private static final QuoteWeek CURRENT = new QuoteWeek(LocalDate.of(2026, 11, 2));
    private static final Instant NOW = CURRENT.start().plus(Duration.ofDays(2));
    private static final List<PlayerBoost> FOUR = List.of(new PlayerBoost(NDIAYE.id(), 300),
            new PlayerBoost(BETO.id(), 200), new PlayerBoost(ZIRKZEE.id(), 250), new PlayerBoost(KANE.id(), 400));
    private static final Map<Long, Player> PLAYERS = Map.of(NDIAYE.id(), NDIAYE, BETO.id(), BETO,
            ZIRKZEE.id(), ZIRKZEE, KANE.id(), KANE);

    @Mock
    private BoostRepository boostRepository;

    @Mock
    private PlayerRepository playerRepository;

    private BoostService service;

    @BeforeEach
    void createService() {
        service = new BoostService(boostRepository, playerRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void losDeLaSemanaEnCursoVienenConSusJugadoresEnElOrdenElegido() {
        when(boostRepository.findWeek(CURRENT)).thenReturn(new WeekBoosts(CURRENT, FOUR));
        when(playerRepository.findIndexedByIds(any())).thenReturn(PLAYERS);

        BoostedWeek current = service.current();

        assertThat(current.week()).isEqualTo(CURRENT);
        assertThat(current.status()).isEqualTo(BoostWeekStatus.CURRENT);
        assertThat(current.players()).extracting(boosted -> boosted.player().name(), BoostedWeek.BoostedPlayer::percent)
                .containsExactly(
                        tuple(NDIAYE.name(), 300),
                        tuple(BETO.name(), 200),
                        tuple(ZIRKZEE.name(), 250),
                        tuple(KANE.name(), 400));
    }

    @Test
    void unaSemanaSinPotenciadosVieneVacia() {
        when(boostRepository.findWeek(CURRENT)).thenReturn(WeekBoosts.none(CURRENT));

        BoostedWeek current = service.current();

        assertThat(current.players()).isEmpty();
    }

    @Test
    void cualquierDiaIndicaLaSemanaQueLoContiene() {
        QuoteWeek next = CURRENT.next();
        when(boostRepository.findWeek(next)).thenReturn(WeekBoosts.none(next));

        BoostedWeek week = service.week(LocalDate.of(2026, 11, 15));

        assertThat(week.week()).isEqualTo(next);
        assertThat(week.status()).isEqualTo(BoostWeekStatus.UPCOMING);
    }

    @Test
    void reemplazarLaSemanaEnCursoGuardaLosCuatro() {
        when(playerRepository.existsById(anyLong())).thenReturn(true);
        when(boostRepository.replace(any())).thenAnswer(call -> call.getArgument(0));
        when(playerRepository.findIndexedByIds(any())).thenReturn(PLAYERS);

        BoostedWeek saved = service.replace(LocalDate.of(2026, 11, 3), FOUR);

        ArgumentCaptor<WeekBoosts> replaced = ArgumentCaptor.forClass(WeekBoosts.class);
        verify(boostRepository).replace(replaced.capture());
        assertThat(replaced.getValue()).isEqualTo(new WeekBoosts(CURRENT, FOUR));
        assertThat(saved.players()).hasSize(4);
    }

    @Test
    void unJugadorInexistenteRespondeNoEncontradoSinGuardar() {
        when(playerRepository.existsById(anyLong())).thenAnswer(call -> !call.getArgument(0).equals(ZIRKZEE.id()));

        assertThatThrownBy(() -> service.replace(CURRENT.monday(), FOUR))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessage("No se encontró el jugador con id " + ZIRKZEE.id() + ".");
        verify(boostRepository, never()).replace(any());
    }

    @Test
    void unaSemanaCerradaNoSeCambiaYNoSeLeeNada() {
        LocalDate lastWeek = CURRENT.previous().monday();

        assertThatThrownBy(() -> service.replace(lastWeek, FOUR)).isInstanceOf(ClosedBoostWeekException.class);
        verifyNoInteractions(boostRepository, playerRepository);
    }

    @Test
    void menosDeCuatroSeRechazaSinGuardar() {
        var three = FOUR.subList(0, 3);

        assertThatThrownBy(() -> service.replace(CURRENT.monday(), three)).isInstanceOf(InvalidBoostException.class);
        verifyNoInteractions(boostRepository, playerRepository);
    }

    @Test
    void unaListaVaciaDejaLaSemanaSinPotenciados() {
        when(boostRepository.replace(any())).thenAnswer(call -> call.getArgument(0));
        when(playerRepository.findIndexedByIds(any())).thenReturn(Map.of());

        BoostedWeek saved = service.replace(CURRENT.next().monday(), List.of());

        verify(boostRepository).replace(WeekBoosts.none(CURRENT.next()));
        assertThat(saved.players()).isEmpty();
    }

    @Test
    void unCambioQueSeCruzaConOtroRespondeConflicto() {
        when(playerRepository.existsById(anyLong())).thenReturn(true);
        when(boostRepository.replace(any())).thenThrow(new DataIntegrityViolationException("ux_boosts_week_player"));

        assertThatThrownBy(() -> service.replace(CURRENT.monday(), FOUR))
                .isInstanceOf(BoostChangeConflictException.class);
    }
}
