package ar.edu.unq.desapp.futbolmarket.controller.market;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.market.dto.PortfolioResponse;
import ar.edu.unq.desapp.futbolmarket.service.market.PortfolioService;
import ar.edu.unq.desapp.futbolmarket.shared.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * El portfolio de una cuenta. Lo ven su dueño y el administrador: el permiso lo decide el modelo
 * antes de buscar la cuenta pedida (research D24), así que acá no se mira el rol.
 */
@RestController
@RequestMapping("/users")
@Validated
@Tag(name = "Portfolio")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
@RequiredArgsConstructor
public class PortfolioController {

    // Los ejemplos del contrato. Son concatenaciones y no format() porque una anotación pide constantes.
    private static final String EXAMPLE_START = "{\"timestamp\": \"2026-10-28T15:00:00Z\", ";
    private static final String UNAUTHORIZED_EXAMPLE = EXAMPLE_START
            + "\"status\": 401, \"error\": \"Unauthorized\", \"message\": \"Se requiere una credencial: un token de "
            + "sesión (Authorization: Bearer) o una clave de API (X-API-Key).\", \"path\": \"/users/7/portfolio\"}";
    private static final String FORBIDDEN_EXAMPLE = EXAMPLE_START
            + "\"status\": 403, \"error\": \"Forbidden\", \"message\": \"No tiene permiso para ver la cuenta de otro "
            + "usuario.\", \"path\": \"/users/8/portfolio\"}";
    private static final String NOT_FOUND_EXAMPLE = EXAMPLE_START
            + "\"status\": 404, \"error\": \"Not Found\", \"message\": \"No se encontró el usuario con id 999.\", "
            + "\"path\": \"/users/999/portfolio\"}";

    private final PortfolioService portfolioService;

    @Operation(
            summary = "Portfolio actual",
            description = "Las tenencias con la cotización vigente de cada jugador (un jugador inactivo, con su "
                    + "última cotización), el saldo libre y reservado y los tokens comprometidos. Para el "
                    + "superusuario, las posiciones son los tokens sin vender de cada jugador y no informan precio "
                    + "promedio, invertido, ganancia ni rentabilidad.")
    @ApiResponse(responseCode = "200", description = "El portfolio.")
    @ApiResponse(responseCode = "400", description = "Identificador inválido.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "No hay credencial, o la credencial es inválida o está vencida.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "Un usuario común pidió los datos de otro usuario.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
    @ApiResponse(responseCode = "404",
            description = "No existe el usuario (solo lo recibe el administrador; un usuario común recibe 403).",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    @GetMapping("/{id}/portfolio")
    public PortfolioResponse getPortfolio(@AuthenticationPrincipal @Parameter(hidden = true) Long requesterId,
                                          @PathVariable @Min(1) Long id) {
        return PortfolioResponse.from(portfolioService.current(requesterId, id));
    }
}
