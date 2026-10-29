package ar.edu.unq.desapp.futbolmarket.controller.market;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.market.dto.MarketOrderPageResponse;
import ar.edu.unq.desapp.futbolmarket.controller.market.dto.OrderExecutionResponse;
import ar.edu.unq.desapp.futbolmarket.controller.market.dto.OrderRequest;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrderFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketOrderService;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketQueryService;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * Los pedidos del mercado y su listado público. Solo los usuarios comunes compran y venden:
 * {@code SecurityConfig} responde 403 al administrador antes de llegar acá (FR-010). El listado
 * ({@code GET /orders}) no pide credencial.
 *
 * <p>El usuario es siempre el de la credencial, nunca uno del cuerpo (FR-013). Los dos
 * requirements se declaran por separado en cada pedido: alcanza con cualquiera de los dos. El tag y
 * los requirements van en los métodos y no en la clase, porque el listado tiene otro tag y es
 * público.</p>
 */
@RestController
@RequestMapping("/orders")
@Validated
@RequiredArgsConstructor
public class OrderController {

    // Los ejemplos del contrato. Son concatenaciones y no format() porque una anotación pide constantes.
    // Cada ejemplo es el comienzo común, su cuerpo y la ruta del pedido.
    private static final String EXAMPLE_START = "{\"timestamp\": \"2026-10-28T15:00:00Z\", ";
    private static final String BUY_PATH = "\"path\": \"/orders/buy\"";
    private static final String SELL_PATH = "\"path\": \"/orders/sell\"";
    private static final String INVALID_BODY = EXAMPLE_START
            + "\"status\": 400, \"error\": \"Bad Request\", \"message\": \"El request tiene datos inválidos.\", ";
    private static final String VIOLATIONS = ", \"violations\": [{\"field\": \"quantity\", \"message\": \""
            + OrderRequest.QUANTITY_MESSAGE + "\"}]}";
    private static final String UNAUTHORIZED = EXAMPLE_START
            + "\"status\": 401, \"error\": \"Unauthorized\", \"message\": \"Se requiere una credencial: un token de "
            + "sesión (Authorization: Bearer) o una clave de API (X-API-Key).\", ";
    private static final String ADMIN = EXAMPLE_START
            + "\"status\": 403, \"error\": \"Forbidden\", \"message\": \"No tiene permisos para acceder a este "
            + "recurso.\", ";
    private static final String PLAYER_NOT_FOUND = EXAMPLE_START
            + "\"status\": 404, \"error\": \"Not Found\", \"message\": \"No se encontró el jugador con id 9999.\", ";
    private static final String CONFLICT = EXAMPLE_START + "\"status\": 409, \"error\": \"Conflict\", \"message\": ";
    private static final String NOT_TRADABLE = CONFLICT
            + "\"El jugador no se puede operar: está inactivo.\", ";

    private static final String INVALID_BODY_EXAMPLE = INVALID_BODY + BUY_PATH + VIOLATIONS;
    private static final String UNAUTHORIZED_EXAMPLE = UNAUTHORIZED + BUY_PATH + "}";
    private static final String ADMIN_EXAMPLE = ADMIN + BUY_PATH + "}";
    private static final String PLAYER_NOT_FOUND_EXAMPLE = PLAYER_NOT_FOUND + BUY_PATH + "}";
    private static final String INSUFFICIENT_CREDITS_EXAMPLE = CONFLICT
            + "\"Saldo insuficiente: se necesitan 10,00 créditos y el saldo libre es 5,00.\", " + BUY_PATH + "}";
    private static final String NOT_TRADABLE_EXAMPLE = NOT_TRADABLE + BUY_PATH + "}";

    private static final String SELL_INVALID_BODY_EXAMPLE = INVALID_BODY + SELL_PATH + VIOLATIONS;
    private static final String SELL_UNAUTHORIZED_EXAMPLE = UNAUTHORIZED + SELL_PATH + "}";
    private static final String SELL_ADMIN_EXAMPLE = ADMIN + SELL_PATH + "}";
    private static final String SELL_PLAYER_NOT_FOUND_EXAMPLE = PLAYER_NOT_FOUND + SELL_PATH + "}";
    private static final String SALE_BLOCKED_EXAMPLE = CONFLICT
            + "\"Todavía no se puede vender: al administrador le queda 1 token de este jugador.\", " + SELL_PATH + "}";
    private static final String INSUFFICIENT_TOKENS_EXAMPLE = CONFLICT
            + "\"Tokens insuficientes: tiene 4 tokens libres de este jugador.\", " + SELL_PATH + "}";
    private static final String SELL_NOT_TRADABLE_EXAMPLE = NOT_TRADABLE + SELL_PATH + "}";

    private static final String INVALID_PARAMETERS_EXAMPLE = EXAMPLE_START
            + "\"status\": 400, \"error\": \"Bad Request\", \"message\": \"Los parámetros enviados no son válidos.\", "
            + "\"path\": \"/orders\"}";

    private final MarketOrderService marketOrderService;
    private final MarketQueryService marketQueryService;

    @Tag(name = "Mercado")
    @Operation(
            summary = "Listado público del mercado",
            description = "Las órdenes con remanente pendiente (PENDING o PARTIAL), de la más vieja a la más nueva "
                    + "(fecha de creación y después id), con la paginación del catálogo. No hay filtro de estado: las "
                    + "ejecutadas y canceladas no se listan. Nunca informa la identidad del dueño, solo su tipo. Los "
                    + "filtros son opcionales y combinables; con ownerType=ADMIN se ven las órdenes iniciales con los "
                    + "tokens que le quedan al superusuario de cada jugador. La ficha del jugador lo usa con "
                    + "playerId.")
    @ApiResponse(responseCode = "200", description = "Una hoja del listado; puede estar vacía.")
    @ApiResponse(responseCode = "400",
            description = "Un filtro con un valor no admitido (tipo, liga o tipo de usuario inexistente, jugador "
                    + "menor que 1 o equipo vacío) o paginación fuera de rango.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = INVALID_PARAMETERS_EXAMPLE)))
    @GetMapping
    public MarketOrderPageResponse market(
            @RequestParam(required = false) OrderSide side,
            @RequestParam(required = false) League league,
            @RequestParam(required = false) @Pattern(regexp = "\\s*\\S.*") String team,
            @RequestParam(required = false) OwnerType ownerType,
            @RequestParam(required = false) @Min(1) Long playerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        MarketOrderFilter filter = new MarketOrderFilter(side, league, team, ownerType, playerId);
        return MarketOrderPageResponse.from(marketQueryService.market(filter, page, size));
    }

    @Tag(name = "Órdenes")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
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

    @Tag(name = "Órdenes")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
    @Operation(
            summary = "Vende tokens de un jugador",
            description = "Se rechaza al crearla, sin crear nada, mientras la orden inicial del jugador tenga tokens "
                    + "pendientes. Si no, compromete los tokens y se cruza con las compras pendientes de otros "
                    + "usuarios, de la más vieja a la más nueva, al precio vigente; lo que no se vende queda como "
                    + "venta pendiente. Una compra pendiente cuyo saldo (su reserva más el saldo libre de su dueño) "
                    + "no alcanza se ejecuta por lo que alcanza y el resto se cancela.")
    @ApiResponse(responseCode = "201",
            description = "La orden creada, con las operaciones que se ejecutaron en el acto.")
    @ApiResponse(responseCode = "400",
            description = "Cuerpo inválido: falta el jugador o la cantidad, o la cantidad no es un entero entre 1 y "
                    + "100 (0, negativa, decimal o mayor que 100). No se crea nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = SELL_INVALID_BODY_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = "No hay credencial, o la credencial es inválida o está vencida.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = SELL_UNAUTHORIZED_EXAMPLE)))
    @ApiResponse(responseCode = "403",
            description = "El administrador no compra ni vende (FR-010). Se rechaza antes del controller.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = SELL_ADMIN_EXAMPLE)))
    @ApiResponse(responseCode = "404", description = "No existe el jugador. No se crea nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = SELL_PLAYER_NOT_FOUND_EXAMPLE)))
    @ApiResponse(responseCode = "409",
            description = "Venta bloqueada porque la orden inicial todavía tiene tokens; tokens libres "
                    + "insuficientes; compras propias pendientes de ese jugador; jugador no operable; mercado ocupado.",
            content = @Content(schema = @Schema(implementation = ApiError.class), examples = {
                    @ExampleObject(name = "bloqueada", value = SALE_BLOCKED_EXAMPLE),
                    @ExampleObject(name = "tokens", value = INSUFFICIENT_TOKENS_EXAMPLE),
                    @ExampleObject(name = "noOperable", value = SELL_NOT_TRADABLE_EXAMPLE)}))
    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderExecutionResponse sell(@AuthenticationPrincipal @Parameter(hidden = true) Long userId,
                                       @Valid @RequestBody OrderRequest request) {
        int quantity = request.quantity().intValueExact();
        return OrderExecutionResponse.from(marketOrderService.sell(userId, request.playerId(), quantity));
    }
}
