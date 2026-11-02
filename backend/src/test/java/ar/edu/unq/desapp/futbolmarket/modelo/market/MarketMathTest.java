package ar.edu.unq.desapp.futbolmarket.modelo.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MarketMathTest {

    private static final int LIMIT = 10;
    private static final BigDecimal ONE_CREDIT = new BigDecimal("1.0000");

    @ParameterizedTest
    @CsvSource({"3, 1.0333, 3.10", "10, 1.0345, 10.35", "10, 1.0000, 10.00", "0, 1.0300, 0.00"})
    void elTotalRedondeaUnaSolaVezACentavosHaciaArribaDesdeLaMitad(int quantity, String tokenPrice, String expected) {
        BigDecimal total = MarketMath.total(quantity, new BigDecimal(tokenPrice));

        assertThat(total).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void conDiezConTreintaAUnoConDiezAlcanzaParaNueveTokens() {
        int quantity = MarketMath.maxAffordable(new BigDecimal("10.30"), new BigDecimal("1.1000"), LIMIT);

        assertThat(quantity).isEqualTo(9);
    }

    @Test
    void unTotalQueRedondeaJustoALoDisponibleAlcanza() {
        int quantity = MarketMath.maxAffordable(new BigDecimal("10.35"), new BigDecimal("1.0345"), LIMIT);

        assertThat(quantity).isEqualTo(10);
    }

    @Test
    void unCentavoMenosQueElTotalRedondeadoNoAlcanzaParaEseToken() {
        int quantity = MarketMath.maxAffordable(new BigDecimal("10.34"), new BigDecimal("1.0345"), LIMIT);

        assertThat(quantity).isEqualTo(9);
    }

    @Test
    void siElRedondeoDelTotalSePasaDeLoDisponibleRestaUnToken() {
        int quantity = MarketMath.maxAffordable(new BigDecimal("10.3451"), new BigDecimal("1.0345"), LIMIT);

        assertThat(quantity).isEqualTo(9);
    }

    @Test
    void noPasaDelTopeAunqueAlcanceParaMas() {
        int quantity = MarketMath.maxAffordable(new BigDecimal("1000.00"), ONE_CREDIT, LIMIT);

        assertThat(quantity).isEqualTo(LIMIT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.99", "0.00", "-1.00"})
    void conMenosDeUnTokenDisponibleNoAlcanzaParaNinguno(String available) {
        int quantity = MarketMath.maxAffordable(new BigDecimal(available), ONE_CREDIT, LIMIT);

        assertThat(quantity).isZero();
    }

    @Test
    void elPrecioPromedioTieneLaEscalaDeUnPrecioPorToken() {
        BigDecimal average = MarketMath.averagePrice(new BigDecimal("15.15"), 15);

        assertThat(average).isEqualTo(new BigDecimal("1.0100"));
    }

    @Test
    void elPorcentajeTieneDosDecimales() {
        Optional<BigDecimal> percent = MarketMath.percent(new BigDecimal("0.30"), new BigDecimal("10.00"));

        assertThat(percent).contains(new BigDecimal("3.00"));
    }

    @Test
    void elPorcentajeDeUnaPerdidaEsNegativo() {
        Optional<BigDecimal> percent = MarketMath.percent(new BigDecimal("-0.30"), new BigDecimal("10.00"));

        assertThat(percent).contains(new BigDecimal("-3.00"));
    }

    @Test
    void sinTodoNoHayPorcentaje() {
        Optional<BigDecimal> percent = MarketMath.percent(new BigDecimal("0.30"), new BigDecimal("0.00"));

        assertThat(percent).isEmpty();
    }
}
