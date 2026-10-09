package ar.edu.unq.desapp.futbolmarket.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.support.CronExpression;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties.Sync;

class FootballDataPropertiesTest {

    private static final String TOKEN = "token-de-prueba-0123456789";
    private static final URI BASE_URL = URI.create("http://football-data.invalid/v4");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String WEEKLY_CRON = "0 0 4 * * MON";
    private static final String ZONE = "America/Argentina/Buenos_Aires";
    private static final String MASKED_VALUE = "****";

    private static final String APPLICATION_YAML = "application.yaml";
    private static final String CONNECT_TIMEOUT_PROPERTY = "futbolmarket.football-data.connect-timeout";
    private static final String READ_TIMEOUT_PROPERTY = "futbolmarket.football-data.read-timeout";

    @ParameterizedTest
    @ValueSource(strings = {WEEKLY_CRON, "-"})
    void aceptaElCronSemanalYElQueDeshabilitaLaCorrida(String cron) {
        Sync sync = new Sync(cron, ZONE, true);

        assertThat(sync.cron()).isEqualTo(cron);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0 4 * * MON", "todos los lunes", ""})
    void rechazaUnCronInvalido(String cron) {
        assertThatThrownBy(() -> new Sync(cron, ZONE, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La expresión cron de la sincronización semanal no es válida: '" + cron + "'.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"America/Mar_del_Plata", "Buenos Aires", ""})
    void rechazaUnaZonaInexistente(String zone) {
        assertThatThrownBy(() -> new Sync(WEEKLY_CRON, zone, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La zona horaria de la sincronización semanal no es válida: '" + zone + "'.");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rechazaUnTiempoDeConexionCeroONegativo(long seconds) {
        Duration connectTimeout = Duration.ofSeconds(seconds);

        assertThatThrownBy(() -> new FootballDataProperties(TOKEN, BASE_URL, connectTimeout, READ_TIMEOUT, sync()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El tiempo máximo de conexión con Football-Data.org tiene que ser positivo.");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rechazaUnTiempoDeLecturaCeroONegativo(long seconds) {
        Duration readTimeout = Duration.ofSeconds(seconds);

        assertThatThrownBy(() -> new FootballDataProperties(TOKEN, BASE_URL, CONNECT_TIMEOUT, readTimeout, sync()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El tiempo máximo de lectura de Football-Data.org tiene que ser positivo.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void noTieneTokenSiFaltaOEstaEnBlanco(String token) {
        FootballDataProperties properties = propertiesWithToken(token);

        assertThat(properties.hasToken()).isFalse();
    }

    @Test
    void tieneTokenSiVieneConValor() {
        FootballDataProperties properties = propertiesWithToken(TOKEN);

        assertThat(properties.hasToken()).isTrue();
    }

    @Test
    void elToStringEnmascaraElToken() {
        FootballDataProperties properties = propertiesWithToken(TOKEN);

        String text = properties.toString();

        assertThat(text)
                .doesNotContain(TOKEN)
                .contains(MASKED_VALUE);
    }

    @Test
    void laConfiguracionBaseFijaDiezSegundosDeConexionYTreintaDeLectura() throws IOException {
        Binder binder = applicationYaml();

        Duration connectTimeout = binder.bind(CONNECT_TIMEOUT_PROPERTY, Duration.class).get();
        Duration readTimeout = binder.bind(READ_TIMEOUT_PROPERTY, Duration.class).get();

        assertThat(connectTimeout).isEqualTo(CONNECT_TIMEOUT);
        assertThat(readTimeout).isEqualTo(READ_TIMEOUT);
    }

    @Test
    void desdeElDomingoALasDiezLaProximaCorridaEsElLunesALasCuatroDeArgentina() throws IOException {
        ZoneId zone = syncZone();
        ZonedDateTime sunday = ZonedDateTime.of(2026, 10, 11, 10, 0, 0, 0, zone);

        ZonedDateTime next = weeklyCron().next(sunday);

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 12, 4, 0, 0, 0, zone));
        assertThat(next.toInstant()).isEqualTo(Instant.parse("2026-10-12T07:00:00Z"));
    }

    @Test
    void desdeElLunesALasCuatroLaProximaCorridaEsElLunesSiguiente() throws IOException {
        ZoneId zone = syncZone();
        ZonedDateTime monday = ZonedDateTime.of(2026, 10, 12, 4, 0, 0, 0, zone);

        ZonedDateTime next = weeklyCron().next(monday);

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 19, 4, 0, 0, 0, zone));
    }

    /**
     * El cron de la corrida semanal, tal como está en {@code application.yaml}.
     */
    private static CronExpression weeklyCron() throws IOException {
        return CronExpression.parse(applicationYaml().bind("futbolmarket.football-data.sync.cron", String.class).get());
    }

    /**
     * La zona en la que se interpreta el cron, tal como está en {@code application.yaml}.
     */
    private static ZoneId syncZone() throws IOException {
        return ZoneId.of(applicationYaml().bind("futbolmarket.football-data.sync.zone", String.class).get());
    }

    private static Sync sync() {
        return new Sync(WEEKLY_CRON, ZONE, true);
    }

    private static FootballDataProperties propertiesWithToken(String token) {
        return new FootballDataProperties(token, BASE_URL, CONNECT_TIMEOUT, READ_TIMEOUT, sync());
    }

    /**
     * Lee {@code application.yaml} sin levantar Spring y convierte los valores con las mismas
     * reglas que el enlace de propiedades (por ejemplo, {@code 10s} a {@link Duration}).
     */
    private static Binder applicationYaml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(APPLICATION_YAML, new ClassPathResource(APPLICATION_YAML));
        return new Binder(ConfigurationPropertySources.from(sources));
    }
}
