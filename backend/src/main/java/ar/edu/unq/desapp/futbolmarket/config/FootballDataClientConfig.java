package ar.edu.unq.desapp.futbolmarket.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP de Football-Data.org (research D3).
 *
 * <p>El token viaja como header por defecto y nunca en la URL. Spring, en DEBUG, solo registra
 * el método y la URL de cada request, así que el token no llega al registro. Por eso no se
 * agregan interceptores de logging.</p>
 *
 * <p>El timeout de lectura del {@link JdkClientHttpRequestFactory} cubre también la lectura del
 * cuerpo, no solo la espera de los headers (FR-037).</p>
 */
@Configuration
public class FootballDataClientConfig {

    public static final String AUTH_TOKEN_HEADER = "X-Auth-Token";

    /**
     * Aplica la URL base y, solo si hay token, el header de autenticación. Es estático para que
     * el test del adapter arme su propio builder con la misma configuración y lo enlace a un
     * servidor simulado.
     */
    public static RestClient.Builder applyDefaults(RestClient.Builder builder, FootballDataProperties properties) {
        builder.baseUrl(properties.baseUrl());
        if (properties.hasToken()) {
            builder.defaultHeader(AUTH_TOKEN_HEADER, properties.token());
        }
        return builder;
    }

    @Bean
    public RestClient footballDataRestClient(FootballDataProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return applyDefaults(RestClient.builder(), properties)
                .requestFactory(requestFactory)
                .build();
    }
}
