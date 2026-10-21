package ar.edu.unq.desapp.futbolmarket.config;

import static ar.edu.unq.desapp.futbolmarket.config.ConfigurationFiles.APPLICATION_TEST_YML;
import static ar.edu.unq.desapp.futbolmarket.config.ConfigurationFiles.APPLICATION_YAML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.scheduling.support.CronExpression;

class QuoteRecalculationPropertiesTest {

    private static final String PREFIX = "futbolmarket.quotes.recalculation";
    private static final String WEEKLY_CRON = "0 0 6 * * MON";
    private static final String ZONE = "America/Argentina/Buenos_Aires";

    @Test
    void laConfiguracionBaseRecalculaLosLunesALasSeisDeArgentinaYAlArrancar() throws IOException {
        Binder binder = ConfigurationFiles.binder(APPLICATION_YAML);

        QuoteRecalculationProperties properties = binder.bind(PREFIX, QuoteRecalculationProperties.class).get();

        assertThat(properties.cron()).isEqualTo(WEEKLY_CRON);
        assertThat(properties.zone()).isEqualTo(ZONE);
        assertThat(properties.onStartup()).isTrue();
    }

    @Test
    void elPerfilTestApagaLaCorridaSemanalYLaDeArranqueYConservaLaZona() throws IOException {
        Binder binder = ConfigurationFiles.binder(APPLICATION_TEST_YML, APPLICATION_YAML);

        QuoteRecalculationProperties properties = binder.bind(PREFIX, QuoteRecalculationProperties.class).get();

        assertThat(properties.cron()).isEqualTo("-");
        assertThat(properties.zone()).isEqualTo(ZONE);
        assertThat(properties.onStartup()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {WEEKLY_CRON, "-"})
    void aceptaElCronSemanalYElQueDeshabilitaLaCorrida(String cron) {
        Binder binder = binderWith(cron, ZONE);

        QuoteRecalculationProperties properties = binder.bind(PREFIX, QuoteRecalculationProperties.class).get();

        assertThat(properties.cron()).isEqualTo(cron);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0 6 * * MON", "todos los lunes", ""})
    void rechazaUnCronInvalidoAlEnlazar(String cron) {
        Binder binder = binderWith(cron, ZONE);

        Throwable thrown = catchThrowable(() -> binder.bind(PREFIX, QuoteRecalculationProperties.class));

        assertThat(thrown).isInstanceOf(BindException.class);
        assertThat(illegalArgumentIn(thrown))
                .hasMessage("La expresión cron del recálculo de cotizaciones no es válida: '" + cron + "'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"America/Mar_del_Plata", "Buenos Aires", ""})
    void rechazaUnaZonaInexistenteAlEnlazar(String zone) {
        Binder binder = binderWith(WEEKLY_CRON, zone);

        Throwable thrown = catchThrowable(() -> binder.bind(PREFIX, QuoteRecalculationProperties.class));

        assertThat(thrown).isInstanceOf(BindException.class);
        assertThat(illegalArgumentIn(thrown))
                .hasMessage("La zona horaria del recálculo de cotizaciones no es válida: '" + zone + "'.");
    }

    @Test
    void desdeElLunesALasCincoLaProximaCorridaEsEseLunesALasSeisDeArgentina() throws IOException {
        QuoteRecalculationProperties properties = ConfigurationFiles.binder(APPLICATION_YAML)
                .bind(PREFIX, QuoteRecalculationProperties.class).get();
        ZoneId zone = ZoneId.of(properties.zone());
        ZonedDateTime monday = ZonedDateTime.of(2026, 10, 12, 5, 0, 0, 0, zone);

        ZonedDateTime next = CronExpression.parse(properties.cron()).next(monday);

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 12, 6, 0, 0, 0, zone));
        assertThat(next.toInstant()).isEqualTo(Instant.parse("2026-10-12T09:00:00Z"));
    }

    /**
     * La validación del constructor, envuelta por el enlace en una o más excepciones.
     */
    private static Throwable illegalArgumentIn(Throwable thrown) {
        Throwable cause = thrown;
        while (cause != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static Binder binderWith(String cron, String zone) {
        return new Binder(new MapConfigurationPropertySource(Map.of(
                PREFIX + ".cron", cron,
                PREFIX + ".zone", zone,
                PREFIX + ".on-startup", "true")));
    }
}
