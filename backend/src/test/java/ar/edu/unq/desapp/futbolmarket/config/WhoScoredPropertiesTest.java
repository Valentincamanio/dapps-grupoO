package ar.edu.unq.desapp.futbolmarket.config;

import static ar.edu.unq.desapp.futbolmarket.config.ConfigurationFiles.APPLICATION_TEST_YML;
import static ar.edu.unq.desapp.futbolmarket.config.ConfigurationFiles.APPLICATION_YAML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;

import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties.Snapshot;

class WhoScoredPropertiesTest {

    private static final URI BASE_URL = URI.create("https://www.whoscored.com");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_INTERVAL = Duration.ofSeconds(4);
    private static final Duration RETRY_WAIT = Duration.ofSeconds(30);
    private static final String SNAPSHOT_PATH = "snapshot/whoscored-stats.jsonl";

    private static final URI TEST_BASE_URL = URI.create("http://whoscored.invalid");
    private static final String TEST_SNAPSHOT_PATH = "build/test-snapshot/absent.jsonl";

    private static final String PREFIX = "futbolmarket.whoscored";
    private static final String REQUEST_INTERVAL_MESSAGE =
            "La pausa entre pedidos a WhoScored tiene que ser de al menos 4 segundos.";
    private static final String RETRY_WAIT_MESSAGE =
            "La espera antes de reintentar un pedido a WhoScored tiene que ser de al menos 30 segundos.";

    @Test
    void aceptaExactamenteCuatroSegundosDePausaYTreintaDeEspera() {
        WhoScoredProperties properties = properties(CONNECT_TIMEOUT, READ_TIMEOUT, REQUEST_INTERVAL, RETRY_WAIT);

        assertThat(properties.requestInterval()).isEqualTo(REQUEST_INTERVAL);
        assertThat(properties.retryWait()).isEqualTo(RETRY_WAIT);
    }

    @Test
    void rechazaUnaPausaDeMenosDeCuatroSegundos() {
        Duration requestInterval = Duration.ofMillis(3999);

        assertThatThrownBy(() -> properties(CONNECT_TIMEOUT, READ_TIMEOUT, requestInterval, RETRY_WAIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(REQUEST_INTERVAL_MESSAGE);
    }

    @Test
    void rechazaUnaEsperaDeMenosDeTreintaSegundos() {
        Duration retryWait = Duration.ofSeconds(29);

        assertThatThrownBy(() -> properties(CONNECT_TIMEOUT, READ_TIMEOUT, REQUEST_INTERVAL, retryWait))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(RETRY_WAIT_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rechazaUnTiempoDeConexionCeroONegativo(long seconds) {
        Duration connectTimeout = Duration.ofSeconds(seconds);

        assertThatThrownBy(() -> properties(connectTimeout, READ_TIMEOUT, REQUEST_INTERVAL, RETRY_WAIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El tiempo máximo de conexión con WhoScored tiene que ser positivo.");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rechazaUnTiempoDeLecturaCeroONegativo(long seconds) {
        Duration readTimeout = Duration.ofSeconds(seconds);

        assertThatThrownBy(() -> properties(CONNECT_TIMEOUT, readTimeout, REQUEST_INTERVAL, RETRY_WAIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El tiempo máximo de lectura de WhoScored tiene que ser positivo.");
    }

    @Test
    void laConfiguracionBaseTraeLaUrlLosHeadersYLosTiemposVerificados() throws IOException {
        Binder binder = ConfigurationFiles.binder(APPLICATION_YAML);

        WhoScoredProperties properties = binder.bind(PREFIX, WhoScoredProperties.class).get();

        assertThat(properties).isEqualTo(properties(CONNECT_TIMEOUT, READ_TIMEOUT, REQUEST_INTERVAL, RETRY_WAIT));
    }

    @Test
    void elPerfilTestApuntaAUnDominioQueNoResuelveYAUnSnapshotInexistente() throws IOException {
        Binder binder = ConfigurationFiles.binder(APPLICATION_TEST_YML, APPLICATION_YAML);

        WhoScoredProperties properties = binder.bind(PREFIX, WhoScoredProperties.class).get();

        assertThat(properties.baseUrl()).isEqualTo(TEST_BASE_URL);
        assertThat(properties.snapshot().path()).isEqualTo(TEST_SNAPSHOT_PATH);
        assertThat(properties.requestInterval()).isEqualTo(REQUEST_INTERVAL);
        assertThat(properties.retryWait()).isEqualTo(RETRY_WAIT);
    }

    @Test
    void conLaConfiguracionBaseElContextoArranca() throws IOException {
        ApplicationContextRunner runner = runnerWithApplicationYaml();

        Throwable failure = startupFailure(runner);

        assertThat(failure).isNull();
    }

    @ParameterizedTest
    @MethodSource("valuesBelowTheMinimum")
    void unaPausaOUnaEsperaPorDebajoDelMinimoImpidenElArranque(String property, String message) throws IOException {
        ApplicationContextRunner runner = runnerWithApplicationYaml().withPropertyValues(property);

        Throwable failure = startupFailure(runner);

        assertThat(failure)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(message);
    }

    static Stream<Arguments> valuesBelowTheMinimum() {
        return Stream.of(
                Arguments.of(PREFIX + ".request-interval=3s", REQUEST_INTERVAL_MESSAGE),
                Arguments.of(PREFIX + ".retry-wait=10s", RETRY_WAIT_MESSAGE));
    }

    private static WhoScoredProperties properties(Duration connectTimeout, Duration readTimeout,
            Duration requestInterval, Duration retryWait) {
        return new WhoScoredProperties(BASE_URL, USER_AGENT, ACCEPT, ACCEPT_LANGUAGE, connectTimeout, readTimeout,
                requestInterval, retryWait, new Snapshot(SNAPSHOT_PATH));
    }

    /**
     * Un contexto mínimo que solo enlaza {@link WhoScoredProperties}, con los valores de
     * {@code application.yaml}. Los que se agregan con {@code withPropertyValues} los tapan.
     */
    private static ApplicationContextRunner runnerWithApplicationYaml() throws IOException {
        List<PropertySource<?>> sources = ConfigurationFiles.load(APPLICATION_YAML);
        return new ApplicationContextRunner()
                .withInitializer(context -> sources.forEach(context.getEnvironment().getPropertySources()::addLast))
                .withUserConfiguration(WhoScoredPropertiesConfiguration.class);
    }

    private static Throwable startupFailure(ApplicationContextRunner runner) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runner.run(context -> failure.set(context.getStartupFailure()));
        return failure.get();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WhoScoredProperties.class)
    static class WhoScoredPropertiesConfiguration {
    }
}
