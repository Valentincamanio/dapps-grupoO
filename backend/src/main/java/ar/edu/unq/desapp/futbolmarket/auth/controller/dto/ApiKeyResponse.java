package ar.edu.unq.desapp.futbolmarket.auth.controller.dto;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.ApiKey;

/**
 * Respuesta de la regeneración. Es el único momento en que la clave nueva viaja en claro: no hay
 * otra vía para volver a consultarla (FR-014).
 */
public record ApiKeyResponse(String apiKey) {

    public static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(apiKey.value());
    }
}
