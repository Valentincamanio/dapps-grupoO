package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Baja las páginas con el {@code RestClient} de WhoScored (research D3). Lee el status y el cuerpo
 * sin que un 403 se vuelva una excepción, y una falla de red o un timeout se vuelve una página
 * fallida: ninguna excepción del cliente HTTP sale del adapter.
 */
@Component
public class HttpPageDownloader implements PageDownloader {
    private static final String TIMEOUT = "timeout";
    private static final String NETWORK_FAILURE = "falla de red";

    private final RestClient restClient;

    public HttpPageDownloader(@Qualifier("whoScoredRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public DownloadedPage download(String path) {
        try {
            return restClient.get()
                    .uri(path)
                    .exchange((request, response) -> DownloadedPage.of(response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            return DownloadedPage.failed(isTimeout(e) ? TIMEOUT : NETWORK_FAILURE);
        }
    }

    /**
     * El timeout se busca en toda la cadena de causas: puede llegar al conectar o al leer el cuerpo.
     */
    private static boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
