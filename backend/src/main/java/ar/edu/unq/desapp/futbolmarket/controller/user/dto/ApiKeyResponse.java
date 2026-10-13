package ar.edu.unq.desapp.futbolmarket.controller.user.dto;

import ar.edu.unq.desapp.futbolmarket.modelo.user.ApiKey;

/**
 * Respuesta de la regeneración. Es el único momento en que la clave nueva viaja en claro: no hay
 * otra vía para volver a consultarla (FR-014).
 */
public record ApiKeyResponse(String apiKey) {

    public static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(apiKey.value());
    }
}
