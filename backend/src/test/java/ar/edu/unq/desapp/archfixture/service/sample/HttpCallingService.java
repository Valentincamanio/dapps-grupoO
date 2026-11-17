package ar.edu.unq.desapp.archfixture.service.sample;

import java.net.http.HttpClient;

// Rompe B5.3 a propósito: el servicio usa un cliente HTTP en vez de un adapter.
public class HttpCallingService {

    private final HttpClient httpClient;

    public HttpCallingService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public HttpClient.Version version() {
        return httpClient.version();
    }
}
