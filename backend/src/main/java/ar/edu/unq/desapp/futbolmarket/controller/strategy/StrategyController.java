package ar.edu.unq.desapp.futbolmarket.controller.strategy;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.strategy.dto.ActivateStrategyRequest;
import ar.edu.unq.desapp.futbolmarket.controller.strategy.dto.CreateStrategyRequest;
import ar.edu.unq.desapp.futbolmarket.controller.strategy.dto.NewVersionRequest;
import ar.edu.unq.desapp.futbolmarket.controller.strategy.dto.StrategyChangeResponse;
import ar.edu.unq.desapp.futbolmarket.controller.strategy.dto.StrategyParametersRequest;
import ar.edu.unq.desapp.futbolmarket.controller.strategy.dto.StrategyResponse;
import ar.edu.unq.desapp.futbolmarket.service.strategy.StrategyChangeService;
import ar.edu.unq.desapp.futbolmarket.service.strategy.StrategyService;
import ar.edu.unq.desapp.futbolmarket.shared.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * La administración de las estrategias de valuación (US4). Todas las rutas, también las de
 * lectura, son solo para administradores: la regla está en {@code SecurityConfig}
 * ({@code /strategies/**}), así que un 401 o un 403 ocurre antes de llegar acá y no cambia nada.
 *
 * <p>Los dos requirements son alternativas, igual que en {@code QuoteAdminController}. Editar y
 * activar rigen desde la semana siguiente: la opción de recotizar en el acto llega con US5.</p>
 */
@RestController
@RequestMapping("/strategies")
@Tag(name = "Estrategias")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
@Validated
@RequiredArgsConstructor
public class StrategyController {

    private static final String UNAUTHORIZED = "Falta la credencial, o es inválida o está vencida. No cambia nada.";
    private static final String FORBIDDEN = "La credencial es válida pero no es de un administrador. No cambia nada.";
    private static final String NOT_FOUND = "No existe una estrategia con ese identificador.";
    private static final String INVALID_STRATEGY = "Datos inválidos: pesos que no suman 1,00, un peso negativo o con "
            + "más de dos decimales, una métrica desconocida, un factor de escala menor o igual a 0, una cotización "
            + "mínima menor a 0,01, un nombre vacío o un cuerpo ilegible. No se crea ni se cambia nada.";

    private final StrategyService strategyService;
    private final StrategyChangeService strategyChangeService;

    @Operation(summary = "Registra una estrategia nueva",
            description = "Crea la versión 1, inactiva. Ninguna cotización cambia y la estrategia activa no cambia. "
                    + "Si se omiten scaleFactor y minimumValue, valen 10 y 10,00. Los pesos van por nombre de "
                    + "métrica y cada juego suma exactamente 1,00.")
    @ApiResponse(responseCode = "201", description = "La estrategia creada, en su versión 1 y sin activar.")
    @ApiResponse(responseCode = "400", description = INVALID_STRATEGY,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Ya existe una estrategia con ese nombre (sin distinguir "
            + "mayúsculas ni espacios de los extremos).", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StrategyResponse create(@Valid @RequestBody CreateStrategyRequest request) {
        StrategyParametersRequest parameters = request.parameters();
        return StrategyResponse.from(strategyService.create(request.name(), parameters.scaleFactor(),
                parameters.minimumValue(), parameters.defaultWeightSet(), parameters.positionWeightSets()));
    }

    @Operation(summary = "Lista las estrategias con todas sus versiones",
            description = "Cada estrategia, en orden de creación, con todas sus versiones y cuál es la activa.")
    @ApiResponse(responseCode = "200", description = "Las estrategias, en orden de creación.")
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping
    public List<StrategyResponse> list() {
        return strategyService.list().stream().map(StrategyResponse::from).toList();
    }

    @Operation(summary = "Consulta una estrategia con todas sus versiones",
            description = "El identificador de una estrategia es el de su versión 1.")
    @ApiResponse(responseCode = "200", description = "La estrategia.")
    @ApiResponse(responseCode = "400", description = "Identificador con formato inválido.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{id}")
    public StrategyResponse get(@PathVariable @Min(1) Long id) {
        return StrategyResponse.from(strategyService.get(id));
    }

    @Operation(summary = "Edita una estrategia (crea su versión siguiente)",
            description = "Crea la versión siguiente con los parámetros enviados; la anterior queda intacta. "
                    + "scaleFactor y minimumValue omitidos conservan los de la versión anterior; los pesos se "
                    + "reemplazan por completo. Si la estrategia no es la activa, la versión nueva queda inactiva "
                    + "y ninguna cotización cambia. Si es la activa, la versión nueva pasa a ser la activa y la usa "
                    + "el próximo cierre semanal; las cotizaciones guardadas conservan la versión con que se "
                    + "calcularon.")
    @ApiResponse(responseCode = "201", description = "La estrategia con su versión nueva.")
    @ApiResponse(responseCode = "400", description = INVALID_STRATEGY,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Otra modificación concurrente chocó con esta. No cambia nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public StrategyChangeResponse createVersion(@PathVariable @Min(1) Long id, @RequestBody NewVersionRequest request) {
        StrategyParametersRequest parameters = request.parameters();
        return StrategyChangeResponse.withoutRequotation(strategyChangeService.createVersion(id,
                parameters.scaleFactor(), parameters.minimumValue(), parameters.defaultWeightSet(),
                parameters.positionWeightSets()));
    }

    @Operation(summary = "Activa una estrategia",
            description = "Deja activa la versión más reciente de la estrategia y la usa el próximo cierre "
                    + "semanal. Activar la que ya es la activa no cambia nada. El cuerpo es opcional.")
    @ApiResponse(responseCode = "200", description = "La estrategia activa.")
    @ApiResponse(responseCode = "400", description = "Identificador con formato inválido o cuerpo ilegible.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Otra modificación concurrente chocó con esta. No cambia nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/{id}/activate")
    public StrategyChangeResponse activate(@PathVariable @Min(1) Long id,
                                           @RequestBody(required = false) ActivateStrategyRequest request) {
        return StrategyChangeResponse.withoutRequotation(strategyChangeService.activate(id));
    }
}
