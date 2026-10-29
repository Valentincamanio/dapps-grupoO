package ar.edu.unq.desapp.futbolmarket.controller.market;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.market.dto.PortfolioResponse;
import ar.edu.unq.desapp.futbolmarket.controller.market.dto.TransactionPageResponse;
import ar.edu.unq.desapp.futbolmarket.controller.market.dto.UserOrderPageResponse;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.TradeFilter;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketQueryService;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * El portfolio, el historial y las órdenes de una cuenta. Los ven su dueño y el administrador: el
 * permiso lo decide el modelo antes de buscar la cuenta pedida (research D24), así que acá no se
 * mira el rol.
 *
 * <p>Las fechas del historial se reciben como texto para que el mensaje de error repita el valor no
 * admitido, como en el historial de cotizaciones.</p>
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
    // Los errores de credencial, permiso y cuenta son los mismos en las tres rutas: cambia solo el path.
    private static final String EXAMPLE_START = "{\"timestamp\": \"2026-10-28T15:00:00Z\", ";
    private static final String UNAUTHORIZED_START = EXAMPLE_START
            + "\"status\": 401, \"error\": \"Unauthorized\", \"message\": \"Se requiere una credencial: un token de "
            + "sesión (Authorization: Bearer) o una clave de API (X-API-Key).\", \"path\": ";
    private static final String FORBIDDEN_START = EXAMPLE_START
            + "\"status\": 403, \"error\": \"Forbidden\", \"message\": \"No tiene permiso para ver la cuenta de otro "
            + "usuario.\", \"path\": ";
    private static final String NOT_FOUND_START = EXAMPLE_START
            + "\"status\": 404, \"error\": \"Not Found\", \"message\": \"No se encontró el usuario con id 999.\", "
            + "\"path\": ";
    private static final String UNAUTHORIZED_EXAMPLE = UNAUTHORIZED_START + "\"/users/7/portfolio\"}";
    private static final String FORBIDDEN_EXAMPLE = FORBIDDEN_START + "\"/users/8/portfolio\"}";
    private static final String NOT_FOUND_EXAMPLE = NOT_FOUND_START + "\"/users/999/portfolio\"}";
    private static final String TRANSACTIONS_UNAUTHORIZED_EXAMPLE = UNAUTHORIZED_START + "\"/users/7/transactions\"}";
    private static final String TRANSACTIONS_FORBIDDEN_EXAMPLE = FORBIDDEN_START + "\"/users/8/transactions\"}";
    private static final String TRANSACTIONS_NOT_FOUND_EXAMPLE = NOT_FOUND_START + "\"/users/999/transactions\"}";
    private static final String ORDERS_UNAUTHORIZED_EXAMPLE = UNAUTHORIZED_START + "\"/users/7/orders\"}";
    private static final String ORDERS_FORBIDDEN_EXAMPLE = FORBIDDEN_START + "\"/users/8/orders\"}";
    private static final String ORDERS_NOT_FOUND_EXAMPLE = NOT_FOUND_START + "\"/users/999/orders\"}";
    private static final String ORDERS_INVALID_PARAMETERS_EXAMPLE = EXAMPLE_START
            + "\"status\": 400, \"error\": \"Bad Request\", \"message\": \"Los parámetros enviados no son válidos.\", "
            + "\"path\": \"/users/7/orders\"}";
    private static final String INVALID_DATE_EXAMPLE = EXAMPLE_START
            + "\"status\": 400, \"error\": \"Bad Request\", \"message\": \"La fecha '2026-13-01' no es válida: use "
            + "el formato AAAA-MM-DD.\", \"path\": \"/users/7/portfolio\"}";
    private static final String INVALID_RANGE_EXAMPLE = EXAMPLE_START
            + "\"status\": 400, \"error\": \"Bad Request\", \"message\": \"El inicio del rango (2026-09-10) es "
            + "posterior al final (2026-09-01).\", \"path\": \"/users/7/transactions\"}";

    private static final String UNAUTHORIZED_DESCRIPTION =
            "No hay credencial, o la credencial es inválida o está vencida.";
    private static final String FORBIDDEN_DESCRIPTION = "Un usuario común pidió los datos de otro usuario.";
    private static final String NOT_FOUND_DESCRIPTION =
            "No existe el usuario (solo lo recibe el administrador; un usuario común recibe 403).";

    private final PortfolioService portfolioService;
    private final MarketQueryService marketQueryService;

    @Operation(
            summary = "Portfolio actual o a una fecha",
            description = "Sin fecha, las tenencias con la cotización vigente de cada jugador (un jugador inactivo, "
                    + "con su última cotización), el saldo libre y reservado y los tokens comprometidos. Con fecha "
                    + "(AAAA-MM-DD en hora de Argentina), las posiciones reconstruidas con las operaciones hasta el "
                    + "final de ese día, valuadas con la cotización que regía entonces; informa solo el saldo total, "
                    + "sin libre, reservado ni comprometidos. Para el superusuario, las posiciones son los tokens sin "
                    + "vender de cada jugador y no informan precio promedio, invertido, ganancia ni rentabilidad.")
    @ApiResponse(responseCode = "200", description = "El portfolio.")
    @ApiResponse(responseCode = "400",
            description = "Identificador inválido, o una fecha con formato inválido, futura o anterior al registro "
                    + "del usuario. El mensaje repite el valor no admitido.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = INVALID_DATE_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    @GetMapping("/{id}/portfolio")
    public PortfolioResponse getPortfolio(
            @AuthenticationPrincipal @Parameter(hidden = true) Long requesterId,
            @PathVariable @Min(1) Long id,
            @Parameter(description = "Fecha AAAA-MM-DD en hora de Argentina; sin ella, el portfolio actual.",
                    example = "2026-08-25")
            @RequestParam(required = false) String date
    ) {
        if (date == null) {
            return PortfolioResponse.from(portfolioService.current(requesterId, id));
        }
        return PortfolioResponse.from(portfolioService.at(requesterId, id, date));
    }

    @Operation(
            summary = "Historial de operaciones de un usuario",
            description = "De la más nueva a la más vieja, con la paginación del catálogo. Filtros opcionales y "
                    + "combinables: jugador, lado (desde el punto de vista del usuario) y rango de fechas AAAA-MM-DD "
                    + "en hora de Argentina, los dos extremos incluidos. La contraparte se informa como ADMIN (el "
                    + "superusuario) o USER (otro usuario), nunca quién. La ganancia realizada va solo en las ventas.")
    @ApiResponse(responseCode = "200", description = "Una hoja del historial; puede estar vacía.")
    @ApiResponse(responseCode = "400",
            description = "Filtro o fecha no admitidos, rango con el inicio posterior al final o paginación fuera de "
                    + "rango.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = INVALID_RANGE_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = TRANSACTIONS_UNAUTHORIZED_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = TRANSACTIONS_FORBIDDEN_EXAMPLE)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = TRANSACTIONS_NOT_FOUND_EXAMPLE)))
    @GetMapping("/{id}/transactions")
    public TransactionPageResponse getTransactions(
            @AuthenticationPrincipal @Parameter(hidden = true) Long requesterId,
            @PathVariable @Min(1) Long id,
            @RequestParam(required = false) @Min(1) Long playerId,
            @RequestParam(required = false) OrderSide side,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        TradeFilter filter = TradeFilter.of(playerId, side, from, to);
        return TransactionPageResponse.from(marketQueryService.transactions(requesterId, id, filter, page, size), id);
    }

    @Operation(
            summary = "Órdenes de un usuario",
            description = "Todas sus órdenes, en cualquier estado, de la más nueva a la más vieja, con la "
                    + "paginación del catálogo.")
    @ApiResponse(responseCode = "200", description = "Una hoja de órdenes; puede estar vacía.")
    @ApiResponse(responseCode = "400", description = "Identificador inválido o paginación fuera de rango.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = ORDERS_INVALID_PARAMETERS_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = ORDERS_UNAUTHORIZED_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = ORDERS_FORBIDDEN_EXAMPLE)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND_DESCRIPTION,
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = ORDERS_NOT_FOUND_EXAMPLE)))
    @GetMapping("/{id}/orders")
    public UserOrderPageResponse getOrders(@AuthenticationPrincipal @Parameter(hidden = true) Long requesterId,
                                           @PathVariable @Min(1) Long id,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return UserOrderPageResponse.from(marketQueryService.ordersOf(requesterId, id, page, size));
    }
}
