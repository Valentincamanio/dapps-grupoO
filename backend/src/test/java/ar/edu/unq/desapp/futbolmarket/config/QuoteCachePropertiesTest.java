package ar.edu.unq.desapp.futbolmarket.config;

import static ar.edu.unq.desapp.futbolmarket.config.ConfigurationFiles.APPLICATION_TEST_YML;
import static ar.edu.unq.desapp.futbolmarket.config.ConfigurationFiles.APPLICATION_YAML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;

import ar.edu.unq.desapp.futbolmarket.FutbolMarketApplication;

/**
 * El tope y el vencimiento de los cachés de cotizaciones (research R5 de la 013): los valores por
 * defecto de {@code application.yaml}, que el perfil test no pisa, y un valor ausente, cero o
 * negativo que impide arrancar con un mensaje que nombra la propiedad (FR-003).
 */
class QuoteCachePropertiesTest {

    private static final String PREFIX = "futbolmarket.cache";
    private static final String RANKING_SIZE = PREFIX + ".ranking.maximum-size";
    private static final String RANKING_EXPIRY = PREFIX + ".ranking.expire-after-write";
    private static final String CURRENT_SIZE = PREFIX + ".current.maximum-size";
    private static final String CURRENT_EXPIRY = PREFIX + ".current.expire-after-write";
    private static final String RANKING_SIZE_MESSAGE =
            "El tope del caché del ranking (" + RANKING_SIZE + ") tiene que ser mayor que cero.";
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    @Test
    void laConfiguracionBaseTopaElRankingEnQuinientosYLaActualEnCincoMilConDiezMinutos() throws IOException {
        Binder binder = ConfigurationFiles.binder(APPLICATION_YAML);

        QuoteCacheProperties properties = binder.bind(PREFIX, QuoteCacheProperties.class).get();

        assertThat(properties.ranking()).isEqualTo(new QuoteCacheProperties.Spec(500L, TEN_MINUTES));
        assertThat(properties.current()).isEqualTo(new QuoteCacheProperties.Spec(5000L, TEN_MINUTES));
    }

    @Test
    void elPerfilTestNoPisaLosValoresDelCache() throws IOException {
        Binder binder = ConfigurationFiles.binder(APPLICATION_TEST_YML, APPLICATION_YAML);

        QuoteCacheProperties properties = binder.bind(PREFIX, QuoteCacheProperties.class).get();

        assertThat(properties.ranking()).isEqualTo(new QuoteCacheProperties.Spec(500L, TEN_MINUTES));
        assertThat(properties.current()).isEqualTo(new QuoteCacheProperties.Spec(5000L, TEN_MINUTES));
    }

    @Test
    void conLaConfiguracionBaseElContextoArranca() throws IOException {
        Throwable failure = startupFailure(runnerWithApplicationYaml());

        assertThat(failure).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void unValorCeroONegativoImpideArrancarConUnMensajeQueNombraLaPropiedad(String property, String value,
            String message) throws IOException {
        ApplicationContextRunner runner = runnerWithApplicationYaml().withPropertyValues(property + "=" + value);

        Throwable failure = startupFailure(runner);

        assertThat(failure)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(message);
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of(RANKING_SIZE, "0", RANKING_SIZE_MESSAGE),
                Arguments.of(RANKING_SIZE, "-1", RANKING_SIZE_MESSAGE),
                Arguments.of(CURRENT_SIZE, "0",
                        "El tope del caché de la cotización actual (" + CURRENT_SIZE + ") tiene que ser mayor que cero."),
                Arguments.of(RANKING_EXPIRY, "0s",
                        "El vencimiento del caché del ranking (" + RANKING_EXPIRY + ") tiene que ser mayor que cero."),
                Arguments.of(RANKING_EXPIRY, "-1m",
                        "El vencimiento del caché del ranking (" + RANKING_EXPIRY + ") tiene que ser mayor que cero."),
                Arguments.of(CURRENT_EXPIRY, "0s", "El vencimiento del caché de la cotización actual ("
                        + CURRENT_EXPIRY + ") tiene que ser mayor que cero."));
    }

    @Test
    void unTopeAusenteImpideArrancarYNombraLaPropiedad() {
        ApplicationContextRunner runner = bareRunner().withPropertyValues(
                RANKING_EXPIRY + "=10m", CURRENT_SIZE + "=5000", CURRENT_EXPIRY + "=10m");

        Throwable failure = startupFailure(runner);

        assertThat(failure)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Falta el tope del caché del ranking (" + RANKING_SIZE + ").");
    }

    @Test
    void unVencimientoAusenteImpideArrancarYNombraLaPropiedad() {
        ApplicationContextRunner runner = bareRunner().withPropertyValues(
                RANKING_SIZE + "=500", RANKING_EXPIRY + "=10m", CURRENT_SIZE + "=5000");

        Throwable failure = startupFailure(runner);

        assertThat(failure)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Falta el vencimiento del caché de la cotización actual (" + CURRENT_EXPIRY + ").");
    }

    @Test
    void unCacheSinConfiguracionImpideArrancarYNombraLaPropiedad() {
        ApplicationContextRunner runner = bareRunner().withPropertyValues(
                RANKING_SIZE + "=500", RANKING_EXPIRY + "=10m");

        Throwable failure = startupFailure(runner);

        assertThat(failure)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Falta la configuración del caché de la cotización actual (futbolmarket.cache.current).");
    }

    /**
     * La aplicación completa, con el perfil test, tampoco arranca con un tope inválido (quickstart
     * §2.4). Falla al enlazar las propiedades, con el valor como argumento de línea de comandos, igual que
     * {@code bootRun --args}.
     */
    @Test
    void laAplicacionCompletaNoArrancaConUnTopeCero() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(FutbolMarketApplication.class)
                .profiles("test")
                .properties("spring.main.web-application-type=none",
                        "spring.datasource.url=jdbc:h2:mem:quote-cache-properties-startup;DB_CLOSE_DELAY=-1");

        Throwable failure = catchThrowable(() -> application.run("--" + RANKING_SIZE + "=0").close());

        assertThat(failure)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(RANKING_SIZE_MESSAGE);
    }

    /**
     * Un contexto mínimo que solo enlaza {@link QuoteCacheProperties}, con los valores de
     * {@code application.yaml}. Los que se agregan con {@code withPropertyValues} los tapan.
     */
    private static ApplicationContextRunner runnerWithApplicationYaml() throws IOException {
        List<PropertySource<?>> sources = ConfigurationFiles.load(APPLICATION_YAML);
        return bareRunner()
                .withInitializer(context -> sources.forEach(context.getEnvironment().getPropertySources()::addLast));
    }

    private static ApplicationContextRunner bareRunner() {
        return new ApplicationContextRunner().withUserConfiguration(QuoteCachePropertiesConfiguration.class);
    }

    private static Throwable startupFailure(ApplicationContextRunner runner) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runner.run(context -> failure.set(context.getStartupFailure()));
        return failure.get();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(QuoteCacheProperties.class)
    static class QuoteCachePropertiesConfiguration {
    }
}
