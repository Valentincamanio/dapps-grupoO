package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.QuoteInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteTest {
    private static final long PLAYER_ID = 9L;
    private static final QuoteWeek WEEK = new QuoteWeek(LocalDate.of(2026, 8, 17));
    private static final StrategyVersionRef STRATEGY_A = new StrategyVersionRef(1L, 1L, "Rendimiento de partido", 1);
    private static final BigDecimal ZERO_SCORE = new BigDecimal("0.000000");

    @Test
    void elPrecioPorTokenEsElValorDivididoEnCienConCuatroDecimales() {
        Quote quote = calculated("103.00", "100.00", "0.300000");

        assertThat(quote.tokenPrice()).isEqualTo(new BigDecimal("1.0300"));
    }

    @Test
    void laVariacionDesde100Es3CreditosY3PorCiento() {
        Quote quote = calculated("103.00", "100.00", "0.300000");

        QuoteVariation variation = quote.variation().orElseThrow();

        assertThat(variation.amount()).isEqualTo(new BigDecimal("3.00"));
        assertThat(variation.percent()).isEqualTo(new BigDecimal("3.00"));
    }

    @Test
    void unaBajaDa2CreditosMenosYElPorcentajeRedondeado() {
        Quote quote = calculated("101.00", "103.00", "-0.200000");

        QuoteVariation variation = quote.variation().orElseThrow();

        assertThat(variation.amount()).isEqualTo(new BigDecimal("-2.00"));
        assertThat(variation.percent()).isEqualTo(new BigDecimal("-1.94"));
    }

    @Test
    void laInicialNoInformaVariacion() {
        Quote quote = initial();

        assertThat(quote.variation()).isEmpty();
        assertThat(quote.tokenPrice()).isEqualTo(new BigDecimal("1.0000"));
    }

    @Test
    void rigeDesdeSuVigenciaIncluida() {
        Quote quote = calculated("103.00", "100.00", "0.300000");

        assertThat(quote.isCurrentAt(WEEK.end().minusSeconds(1))).isFalse();
        assertThat(quote.isCurrentAt(WEEK.end())).isTrue();
    }

    @Test
    void laInicialTieneQueRegirDesdeElInicioDeLaSemana() {
        assertThatThrownBy(() -> new Quote(null, PLAYER_ID, WEEK, WEEK.end(), Quote.INITIAL_VALUE, null, ZERO_SCORE,
                QuoteType.INITIAL, STRATEGY_A))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void laCalculadaTieneQueRegirDesdeElCierreDeLaSemana() {
        assertThatThrownBy(() -> new Quote(null, PLAYER_ID, WEEK, WEEK.start(), new BigDecimal("103.00"),
                new BigDecimal("100.00"), new BigDecimal("0.300000"), QuoteType.CALCULATED, STRATEGY_A))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void laRecotizadaPuedeRegirDesdeCualquierInstante() {
        Instant now = Instant.parse("2026-08-26T15:00:00Z");

        Quote quote = new Quote(null, PLAYER_ID, WEEK, now, new BigDecimal("104.00"), new BigDecimal("100.00"),
                new BigDecimal("0.400000"), QuoteType.REQUOTED, STRATEGY_A);

        assertThat(quote.validFrom()).isEqualTo(now);
    }

    @Test
    void elValorTieneQueTenerDosDecimales() {
        assertThatThrownBy(() -> calculated("103.0", "100.00", "0.300000"))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void elValorTieneQueSerPositivo() {
        assertThatThrownBy(() -> calculated("0.00", "100.00", "-1.000000"))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void elScoreTieneQueTenerSeisDecimales() {
        assertThatThrownBy(() -> calculated("103.00", "100.00", "0.3"))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void elScoreNoPuedeSalirseDeMenosUnoAMasUno() {
        assertThatThrownBy(() -> calculated("111.00", "100.00", "1.100000"))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void laInicialNoTieneValorBase() {
        assertThatThrownBy(() -> new Quote(null, PLAYER_ID, WEEK, WEEK.start(), Quote.INITIAL_VALUE,
                Quote.INITIAL_VALUE, ZERO_SCORE, QuoteType.INITIAL, STRATEGY_A))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void laCalculadaYLaRecotizadaExigenValorBase() {
        assertThatThrownBy(() -> new Quote(null, PLAYER_ID, WEEK, WEEK.end(), new BigDecimal("103.00"), null,
                new BigDecimal("0.300000"), QuoteType.CALCULATED, STRATEGY_A))
                .isInstanceOf(QuoteInvariantException.class);
        assertThatThrownBy(() -> new Quote(null, PLAYER_ID, WEEK, WEEK.end(), new BigDecimal("103.00"), null,
                new BigDecimal("0.300000"), QuoteType.REQUOTED, STRATEGY_A))
                .isInstanceOf(QuoteInvariantException.class);
    }

    @Test
    void laEstrategiaEsObligatoria() {
        assertThatThrownBy(() -> new Quote(null, PLAYER_ID, WEEK, WEEK.start(), Quote.INITIAL_VALUE, null,
                ZERO_SCORE, QuoteType.INITIAL, null))
                .isInstanceOf(QuoteInvariantException.class);
    }

    private static Quote initial() {
        return new Quote(null, PLAYER_ID, WEEK, WEEK.start(), Quote.INITIAL_VALUE, null, ZERO_SCORE,
                QuoteType.INITIAL, STRATEGY_A);
    }

    private static Quote calculated(String value, String baseValue, String score) {
        return new Quote(null, PLAYER_ID, WEEK, WEEK.end(), new BigDecimal(value), new BigDecimal(baseValue),
                new BigDecimal(score), QuoteType.CALCULATED, STRATEGY_A);
    }
}
