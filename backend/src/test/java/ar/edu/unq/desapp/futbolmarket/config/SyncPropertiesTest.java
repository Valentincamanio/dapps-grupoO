package ar.edu.unq.desapp.futbolmarket.config;

import static ar.edu.unq.desapp.futbolmarket.config.ConfigurationFiles.APPLICATION_YAML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;

class SyncPropertiesTest {

    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    @Test
    void laConfiguracionBaseVenceLaUltimaCompletaALosSieteDias() throws IOException {
        Binder binder = ConfigurationFiles.binder(APPLICATION_YAML);

        SyncProperties properties = binder.bind("futbolmarket.sync", SyncProperties.class).get();

        assertThat(properties.catchUpAfter()).isEqualTo(SEVEN_DAYS);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rechazaUnPlazoCeroONegativo(long days) {
        Duration catchUpAfter = Duration.ofDays(days);

        assertThatThrownBy(() -> new SyncProperties(catchUpAfter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El plazo de vencimiento de la última sincronización completa tiene que ser positivo.");
    }
}
