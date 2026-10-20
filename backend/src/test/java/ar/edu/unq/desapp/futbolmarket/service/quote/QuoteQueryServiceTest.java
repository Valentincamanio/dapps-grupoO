package ar.edu.unq.desapp.futbolmarket.service.quote;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.NDIAYE;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.STRATEGY_A;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.STRATEGY_B;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.WEEK;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.ZIRKZEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.PlayerNotFoundException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteDay;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankedQuote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RankingPage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.FutureQuoteDateException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidDateRangeException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidQuoteDateException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.PlayerWithoutQuoteException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteNotFoundAtDateException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.quote.QuoteRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;

/**
 * Las consultas con el reloj fijo el jueves 15/10/2026 a las 12:00 de Argentina.
 */
@ExtendWith(MockitoExtension.class)
class QuoteQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");
    private static final long MISSING_ID = 9_999L;

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private StrategyRepository strategyRepository;

    private QuoteQueryService service;

    @BeforeEach
    void setUp() {
        service = new QuoteQueryService(quoteRepository, playerRepository, strategyRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void laActualDeUnJugadorConCotizacionNoBuscaAlJugador() {
        Quote calculated = QuoteFixtures.calculated(NDIAYE.id(), WEEK, "103.00", "100.00", "0.300000", STRATEGY_A);
        when(quoteRepository.findCurrent(NDIAYE.id())).thenReturn(Optional.of(calculated));

        Quote current = service.getCurrent(NDIAYE.id());

        assertThat(current).isEqualTo(calculated);
        verifyNoInteractions(playerRepository);
    }

    @Test
    void laActualDeUnJugadorInexistenteEsJugadorNoEncontrado() {
        when(quoteRepository.findCurrent(MISSING_ID)).thenReturn(Optional.empty());
        when(playerRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(MISSING_ID)).isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void laActualDeUnJugadorExistenteSinCotizacionTieneSuPropioMensaje() {
        when(quoteRepository.findCurrent(NDIAYE.id())).thenReturn(Optional.empty());
        when(playerRepository.findById(NDIAYE.id())).thenReturn(Optional.of(NDIAYE));

        assertThatThrownBy(() -> service.getCurrent(NDIAYE.id()))
                .isInstanceOf(PlayerWithoutQuoteException.class)
                .hasMessage("El jugador " + NDIAYE.id() + " no tiene cotización.");
    }

    @Test
    void laDeUnaFechaBuscaLaQueRegiaAlFinalDeEseDiaEnHoraDeArgentina() {
        Quote initial = QuoteFixtures.initial(NDIAYE.id(), WEEK, STRATEGY_A);
        LocalDate day = LocalDate.of(2026, 8, 20);
        when(quoteRepository.findAt(NDIAYE.id(), QuoteDay.endOf(day))).thenReturn(Optional.of(initial));

        Quote quote = service.getAt(NDIAYE.id(), "2026-08-20");

        assertThat(quote).isEqualTo(initial);
    }

    /**
     * Hoy es el 15/10 en Argentina: el 15/10 se puede pedir y el 16/10 es futuro.
     */
    @Test
    void hoySePuedePedirYManianaEsFuturo() {
        when(quoteRepository.findAt(NDIAYE.id(), QuoteDay.endOf(LocalDate.of(2026, 10, 15))))
                .thenReturn(Optional.of(QuoteFixtures.initial(NDIAYE.id(), WEEK, STRATEGY_A)));

        assertThat(service.getAt(NDIAYE.id(), "2026-10-15")).isNotNull();
        assertThatThrownBy(() -> service.getAt(NDIAYE.id(), "2026-10-16"))
                .isInstanceOf(FutureQuoteDateException.class)
                .hasMessage("La fecha 2026-10-16 es futura.");
    }

    @Test
    void unaFechaInvalidaSeRechazaSinConsultarNada() {
        assertThatThrownBy(() -> service.getAt(NDIAYE.id(), "2026-13-45"))
                .isInstanceOf(InvalidQuoteDateException.class)
                .hasMessageContaining("'2026-13-45'");
        verifyNoInteractions(quoteRepository, playerRepository);
    }

    @Test
    void sinCotizacionAEsaFechaDistingueAlJugadorInexistente() {
        LocalDate day = LocalDate.of(2026, 8, 16);
        when(quoteRepository.findAt(anyLong(), any())).thenReturn(Optional.empty());
        when(playerRepository.findById(NDIAYE.id())).thenReturn(Optional.of(NDIAYE));
        when(playerRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAt(NDIAYE.id(), day.toString()))
                .isInstanceOf(QuoteNotFoundAtDateException.class)
                .hasMessage("El jugador " + NDIAYE.id() + " no tiene cotización al 2026-08-16.");
        assertThatThrownBy(() -> service.getAt(MISSING_ID, day.toString()))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void elHistorialIncluyeLosDosExtremosCompletos() {
        QuotePage<Quote> history = new QuotePage<>(
                List.of(QuoteFixtures.initial(NDIAYE.id(), WEEK, STRATEGY_A)), 0, 20, 1);
        Instant from = QuoteDay.startOf(LocalDate.of(2026, 8, 17));
        Instant to = QuoteDay.endOf(LocalDate.of(2026, 8, 31));
        when(quoteRepository.findHistory(NDIAYE.id(), from, to, 0, 20)).thenReturn(history);

        QuotePage<Quote> page = service.getHistory(NDIAYE.id(), "2026-08-17", "2026-08-31", 0, 20);

        assertThat(page).isEqualTo(history);
        verifyNoInteractions(playerRepository);
    }

    @Test
    void elHistorialSinRangoVaDesdeElPrincipioHastaElFinalDeHoy() {
        QuotePage<Quote> empty = new QuotePage<>(List.of(), 0, 20, 0);
        when(quoteRepository.findHistory(NDIAYE.id(), Instant.EPOCH, QuoteDay.endOf(LocalDate.of(2026, 10, 15)), 0,
                20)).thenReturn(empty);
        when(playerRepository.findById(NDIAYE.id())).thenReturn(Optional.of(NDIAYE));

        QuotePage<Quote> page = service.getHistory(NDIAYE.id(), null, null, 0, 20);

        assertThat(page.content()).isEmpty();
    }

    @Test
    void elHistorialVacioDeUnJugadorInexistenteEsJugadorNoEncontrado() {
        when(quoteRepository.findHistory(anyLong(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new QuotePage<>(List.of(), 0, 20, 0));
        when(playerRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(MISSING_ID, null, null, 0, 20))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void unRangoInvertidoSeRechazaSinConsultarNada() {
        assertThatThrownBy(() -> service.getHistory(NDIAYE.id(), "2026-09-01", "2026-08-01", 0, 20))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessage("El inicio del rango (2026-09-01) es posterior al final (2026-08-01).");
        verifyNoInteractions(quoteRepository, playerRepository);
    }

    @Test
    void unExtremoDelRangoConFormatoInvalidoSeRechazaConSuValor() {
        assertThatThrownBy(() -> service.getHistory(NDIAYE.id(), "2026-08-01", "31/08/2026", 0, 20))
                .isInstanceOf(InvalidQuoteDateException.class)
                .hasMessageContaining("'31/08/2026'");
    }

    @Test
    void elLoteOmiteALosQueNoTienenCotizacionYRespetaElOrdenPedidoSinRepetidos() {
        Quote ndiaye = QuoteFixtures.calculated(NDIAYE.id(), WEEK, "103.00", "100.00", "0.300000", STRATEGY_A);
        Quote zirkzee = QuoteFixtures.initial(ZIRKZEE.id(), WEEK, STRATEGY_A);
        List<Long> requested = List.of(ZIRKZEE.id(), MISSING_ID, NDIAYE.id());
        when(quoteRepository.findCurrentOf(requested)).thenReturn(List.of(ndiaye, zirkzee));

        List<Quote> quotes = service.getCurrentOf(List.of(ZIRKZEE.id(), MISSING_ID, NDIAYE.id(), ZIRKZEE.id()));

        assertThat(quotes).containsExactly(zirkzee, ndiaye);
    }

    @Test
    void elLoteDeMasDe50JugadoresSeRechaza() {
        List<Long> ids = LongStream.rangeClosed(1, 51).boxed().toList();

        assertThatThrownBy(() -> service.getCurrentOf(ids)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(quoteRepository);
    }

    @Test
    void elRankingInformaLaEstrategiaActiva() {
        PlayerFilter filter = new PlayerFilter(null, null, null);
        Quote quote = QuoteFixtures.calculated(NDIAYE.id(), WEEK, "103.00", "100.00", "0.300000", STRATEGY_A);
        QuotePage<RankedQuote> page = new QuotePage<>(List.of(new RankedQuote(NDIAYE, quote)), 0, 10, 1);
        when(quoteRepository.findRanking(filter, 0, 10)).thenReturn(page);
        when(strategyRepository.findCatalog()).thenReturn(new StrategyCatalog(List.of(
                new Strategy(STRATEGY_A.id(), STRATEGY_A.name(), List.of(STRATEGY_A)),
                new Strategy(STRATEGY_B.id(), STRATEGY_B.name(), List.of(STRATEGY_B))), STRATEGY_B.id()));

        RankingPage ranking = service.getRanking(filter, 0, 10);

        assertThat(ranking.page()).isEqualTo(page);
        assertThat(ranking.activeStrategy()).isEqualTo(STRATEGY_B.ref());
        verify(quoteRepository).findRanking(filter, 0, 10);
    }
}
