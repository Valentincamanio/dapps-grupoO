package ar.edu.unq.desapp.futbolmarket.config;

import static ar.edu.unq.desapp.futbolmarket.config.ConfigurationFiles.APPLICATION_YAML;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WhoScoredClientConfigTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private HttpClient httpClient;

    @AfterEach
    void closeHttpClient() {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    void elClienteSigueLasRedirecciones() throws IOException {
        WhoScoredProperties properties = applicationYamlProperties();

        httpClient = WhoScoredClientConfig.httpClient(properties);

        assertThat(httpClient.followRedirects()).isEqualTo(HttpClient.Redirect.NORMAL);
    }

    @Test
    void elClienteTieneDiezSegundosDeTiempoMaximoDeConexion() throws IOException {
        WhoScoredProperties properties = applicationYamlProperties();

        httpClient = WhoScoredClientConfig.httpClient(properties);

        assertThat(httpClient.connectTimeout()).contains(CONNECT_TIMEOUT);
    }

    private static WhoScoredProperties applicationYamlProperties() throws IOException {
        return ConfigurationFiles.binder(APPLICATION_YAML)
                .bind("futbolmarket.whoscored", WhoScoredProperties.class)
                .get();
    }
}
