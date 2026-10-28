package ar.edu.unq.desapp.futbolmarket.persistence.repository.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteType;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;

/**
 * La cotización que rige en un instante, el precio de una operación del mercado (research D14 de la
 * 008), con una H2 propia. La estrategia es la que siembra {@code StrategySeeder} al arrancar.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:quote-repository-market-it;DB_CLOSE_DELAY=-1")
class QuoteRepositoryMarketIT {
    private static final QuoteWeek WEEK = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final Instant REQUOTED_AT = WEEK.end().plus(Duration.ofDays(2));
    private static final BigDecimal ZERO_SCORE = new BigDecimal("0.000000");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private StrategyRepository strategyRepository;

    private QuoteDatabase database;
    private StrategyVersionRef strategy;
    private Player kane;
    private Player coleman;
    private Player son;

    @BeforeEach
    void prepareDatabase() {
        database = new QuoteDatabase(context);
        database.clean();
        strategy = strategyRepository.findCatalog().activeVersion().ref();
        Team team = database.team("57", "Arsenal FC");
        kane = database.player("8300", "Harry Kane", Position.FORWARD, team);
        coleman = database.inactive(database.player("8304", "Seamus Coleman", Position.DEFENDER, team));
        son = database.player("8302", "Son Heung-min", Position.FORWARD, team);
        quoteRepository.saveAll(List.of(
                initial(kane),
                new Quote(null, kane.id(), WEEK, WEEK.end(), new BigDecimal("103.00"), Quote.INITIAL_VALUE,
                        ZERO_SCORE, QuoteType.CALCULATED, strategy),
                new Quote(null, kane.id(), WEEK.next(), REQUOTED_AT, new BigDecimal("105.00"),
                        new BigDecimal("103.00"), ZERO_SCORE, QuoteType.REQUOTED, strategy),
                initial(coleman)));
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void devuelveLaUltimaConVigenciaAnteriorAlInstante() {
        Optional<Quote> inForce = quoteRepository.findInForceAt(kane.id(), WEEK.end().plus(Duration.ofHours(1)));

        assertThat(inForce).hasValueSatisfying(quote -> {
            assertThat(quote.type()).isEqualTo(QuoteType.CALCULATED);
            assertThat(quote.value()).isEqualTo(new BigDecimal("103.00"));
            assertThat(quote.tokenPrice()).isEqualTo(new BigDecimal("1.0300"));
        });
    }

    @Test
    void unaCotizacionRigeDesdeElInstanteMismoDeSuVigencia() {
        Optional<Quote> inForce = quoteRepository.findInForceAt(kane.id(), WEEK.end());

        assertThat(inForce).hasValueSatisfying(quote -> assertThat(quote.type()).isEqualTo(QuoteType.CALCULATED));
    }

    @Test
    void ignoraUnaCotizacionConVigenciaFutura() {
        Optional<Quote> inForce = quoteRepository.findInForceAt(kane.id(), REQUOTED_AT.minusSeconds(1));

        assertThat(inForce).hasValueSatisfying(quote -> assertThat(quote.value()).isEqualTo(new BigDecimal("103.00")));
    }

    @Test
    void devuelveLaDeUnJugadorInactivo() {
        Optional<Quote> inForce = quoteRepository.findInForceAt(coleman.id(), WEEK.end());

        assertThat(inForce).hasValueSatisfying(quote -> assertThat(quote.playerId()).isEqualTo(coleman.id()));
    }

    @Test
    void unJugadorSinCotizacionesNoTieneNingunaVigente() {
        Optional<Quote> inForce = quoteRepository.findInForceAt(son.id(), WEEK.end());

        assertThat(inForce).isEmpty();
    }

    @Test
    void antesDeLaPrimeraCotizacionNoHayNingunaVigente() {
        Optional<Quote> inForce = quoteRepository.findInForceAt(kane.id(), WEEK.start().minusSeconds(1));

        assertThat(inForce).isEmpty();
    }

    private Quote initial(Player player) {
        return new Quote(null, player.id(), WEEK, WEEK.start(), Quote.INITIAL_VALUE, null, ZERO_SCORE,
                QuoteType.INITIAL, strategy);
    }
}
