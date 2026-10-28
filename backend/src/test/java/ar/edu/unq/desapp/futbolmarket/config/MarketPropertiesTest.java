package ar.edu.unq.desapp.futbolmarket.config;

import static ar.edu.unq.desapp.futbolmarket.config.ConfigurationFiles.APPLICATION_TEST_YML;
import static ar.edu.unq.desapp.futbolmarket.config.ConfigurationFiles.APPLICATION_YAML;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;

class MarketPropertiesTest {

    private static final String PREFIX = "futbolmarket.market";

    @Test
    void laConfiguracionBaseGeneraLasOrdenesInicialesSolas() throws IOException {
        Binder binder = ConfigurationFiles.binder(APPLICATION_YAML);

        MarketProperties properties = binder.bind(PREFIX, MarketProperties.class).get();

        assertThat(properties.initialOrders().automatic()).isTrue();
    }

    @Test
    void elPerfilTestApagaLaGeneracionAutomaticaDeLasOrdenesIniciales() throws IOException {
        Binder binder = ConfigurationFiles.binder(APPLICATION_TEST_YML, APPLICATION_YAML);

        MarketProperties properties = binder.bind(PREFIX, MarketProperties.class).get();

        assertThat(properties.initialOrders().automatic()).isFalse();
    }
}
