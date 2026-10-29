package ar.edu.unq.desapp.futbolmarket.controller.market;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.market.dto.OrderExecutionResponse;
import ar.edu.unq.desapp.futbolmarket.controller.market.dto.OrderRequest;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketOrderService;
import ar.edu.unq.desapp.futbolmarket.shared.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Los pedidos del mercado. Solo los usuarios comunes compran y venden: {@code SecurityConfig}
 * responde 403 al administrador antes de llegar acá (FR-010).
 *
 * <p>El usuario es siempre el de la credencial, nunca uno del cuerpo (FR-013). Los dos
 * requirements se declaran por separado: alcanza con cualquiera de los dos.</p>
 */
@RestController
@RequestMapping("/orders")
@Tag(name = "Órdenes")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
@RequiredArgsConstructor
public class OrderController {

    // Los ejemplos del contrato. Son concatenaciones y no format() porque una anotación pide constantes.
    private static final String EXAMPLE_START = "{\"timestamp\": \"2026-10-28T15:00:00Z\", ";
    private static final String EXAMPLE_PATH = "\"path\": \"/orders/buy\"";
    private static final String INVALID_BODY_EXAMPLE = EXAMPLE_START
            + "\"status\": 400, \"error\": \"Bad Request\", \"message\": \"El request tiene datos inválidos.\", "
            + EXAMPLE_PATH + ", \"violations\": [{\"field\": \"quantity\", \"message\": \""
            + OrderRequest.QUANTITY_MESSAGE + "\"}]}";
    private static final String UNAUTHORIZED_EXAMPLE = EXAMPLE_START
            + "\"status\": 401, \"error\": \"Unauthorized\", \"message\": \"Se requiere una credencial: un token de "
            + "sesión (Authorization: Bearer) o una clave de API (X-API-Key).\", " + EXAMPLE_PATH + "}";
    private static final String ADMIN_EXAMPLE = EXAMPLE_START
            + "\"status\": 403, \"error\": \"Forbidden\", \"message\": \"No tiene permisos para acceder a este "
            + "recurso.\", " + EXAMPLE_PATH + "}";
    private static final String PLAYER_NOT_FOUND_EXAMPLE = EXAMPLE_START
            + "\"status\": 404, \"error\": \"Not Found\", \"message\": \"No se encontró el jugador con id 9999.\", "
            + EXAMPLE_PATH + "}";
    private static final String INSUFFICIENT_CREDITS_EXAMPLE = EXAMPLE_START
            + "\"status\": 409, \"error\": \"Conflict\", \"message\": \"Saldo insuficiente: se necesitan 10,00 "
            + "créditos y el saldo libre es 5,00.\", " + EXAMPLE_PATH + "}";
    private static final String NOT_TRADABLE_EXAMPLE = EXAMPLE_START
            + "\"status\": 409, \"error\": \"Conflict\", \"message\": \"El jugador no se puede operar: está "
            + "inactivo.\", " + EXAMPLE_PATH + "}";

    private final MarketOrderService marketOrderService;

    @Operation(
            summary = "Compra tokens de un jugador",
            description = "Se ejecuta en el acto contra la orden inicial del jugador (a 1,0000) y contra las ventas "
                    + "pendientes de otros usuarios (al precio vigente), en ese orden; lo que falta queda como compra "
                    + "pendiente y reserva créditos al precio vigente. El pedido se rechaza entero si el saldo libre "
                    + "no alcanza para la parte que se ejecuta más la reserva del remanente: entonces no se crea "
                    + "ninguna orden ni cambia ningún saldo.")
    @ApiResponse(responseCode = "201",
            description = "La orden creada, con las operaciones que se ejecutaron en el acto.")
    @ApiResponse(responseCode = "400",
            description = "Cuerpo inválido: falta el jugador o la cantidad, o la cantidad no es un entero entre 1 y "
                    + "100 (0, negativa, decimal o mayor que 100). No se crea nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = INVALID_BODY_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = "No hay credencial, o la credencial es inválida o está vencida.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    @ApiResponse(responseCode = "403",
            description = "El administrador no compra ni vende (FR-010). Se rechaza antes del controller.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = ADMIN_EXAMPLE)))
    @ApiResponse(responseCode = "404", description = "No existe el jugador. No se crea nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = PLAYER_NOT_FOUND_EXAMPLE)))
    @ApiResponse(responseCode = "409",
            description = "Saldo insuficiente; ventas propias pendientes de ese jugador; jugador no operable "
                    + "(inactivo, sin cotización o sin su orden inicial); mercado ocupado.",
            content = @Content(schema = @Schema(implementation = ApiError.class), examples = {
                    @ExampleObject(name = "saldo", value = INSUFFICIENT_CREDITS_EXAMPLE),
                    @ExampleObject(name = "noOperable", value = NOT_TRADABLE_EXAMPLE)}))
    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderExecutionResponse buy(@AuthenticationPrincipal @Parameter(hidden = true) Long userId,
                                      @Valid @RequestBody OrderRequest request) {
        int quantity = request.quantity().intValueExact();
        return OrderExecutionResponse.from(marketOrderService.buy(userId, request.playerId(), quantity));
    }
}
