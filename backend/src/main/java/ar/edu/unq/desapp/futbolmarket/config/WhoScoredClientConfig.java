package ar.edu.unq.desapp.futbolmarket.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP de WhoScored (research D3).
 *
 * <p>Sigue las redirecciones: si WhoScored redirige a la URL con el <em>slug</em>, la página
 * tiene que llegar con su bloque de datos y no como un 301, que se tomaría como un rechazo.</p>
 *
 * <p>La compresión queda apagada y no se manda {@code Accept-Encoding} ni cookies: es la
 * combinación verificada contra el sitio, y el cliente del JDK no descomprime solo. No hay
 * interceptores de logging. El timeout de lectura cubre también el cuerpo, que en la página de un
 * partido llega a 1,3 MB.</p>
 */
@Configuration
public class WhoScoredClientConfig {

    /**
     * Aplica la URL base y los headers verificados. Es estático para que el test del adapter arme
     * su propio builder con la misma configuración y lo enlace a un servidor simulado.
     */
    public static RestClient.Builder applyDefaults(RestClient.Builder builder, WhoScoredProperties properties) {
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, properties.accept())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, properties.acceptLanguage());
    }

    /**
     * Arma el cliente del JDK con la versión por defecto: HTTP/2, con <em>fallback</em> a
     * HTTP/1.1. Es estático para probar sin red las redirecciones y el timeout de conexión.
     */
    public static HttpClient httpClient(WhoScoredProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    public RestClient whoScoredRestClient(WhoScoredProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient(properties));
        requestFactory.setReadTimeout(properties.readTimeout());
        return applyDefaults(RestClient.builder(), properties)
                .requestFactory(requestFactory)
                .build();
    }
}
