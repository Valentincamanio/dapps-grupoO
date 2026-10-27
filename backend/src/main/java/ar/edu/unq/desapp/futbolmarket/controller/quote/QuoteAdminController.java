package ar.edu.unq.desapp.futbolmarket.controller.quote;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.RecalculationReportResponse;
import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.SimulationPageResponse;
import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.SimulationRequest;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteRecalculationService;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteSimulationService;
import ar.edu.unq.desapp.futbolmarket.shared.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
 * Disparo manual del recálculo de las cotizaciones y simulación de una estrategia. Solo un
 * administrador puede usarlos: la regla está en {@code SecurityConfig} ({@code POST /quotes/**}),
 * así que un rechazo ocurre antes de llegar acá y no se cotiza ni se simula nada (FR-053).
 *
 * <p>Los dos requirements son alternativas, igual que en {@code SyncController}. Cada operación
 * declara su propio tag, como en {@code QuoteController}.</p>
 */
@RestController
@RequestMapping("/quotes")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
@Validated
@RequiredArgsConstructor
public class QuoteAdminController {

    private final QuoteRecalculationService recalculationService;
    private final QuoteSimulationService simulationService;

    @Tag(name = "Recálculo")
    @Operation(
            summary = "Dispara el recálculo de las cotizaciones",
            description = "Sin cuerpo ni parámetros. Cotiza, semana cerrada por semana cerrada y en orden, a todos "
                    + "los jugadores activos con la estrategia activa, y devuelve el informe: las semanas "
                    + "cotizadas (con la cantidad de cotizaciones iniciales, calculadas y recotizadas de cada una) "
                    + "y las pendientes con su motivo. Cada semana se guarda completa o no deja nada. Una semana "
                    + "con partidos terminados sin estadísticas espera 7 días desde su cierre, y una pendiente "
                    + "bloquea las siguientes. La semana en curso nunca se cotiza y repetir el recálculo no crea "
                    + "cotizaciones nuevas. No consulta ninguna fuente externa. El request espera a que termine "
                    + "y devuelve 200 también si no había semanas para cotizar (nothingToQuote); la primera "
                    + "reconstrucción de una temporada puede demorar minutos.")
    @ApiResponse(responseCode = "200", description = "Recálculo terminado. Devuelve el informe, que puede estar vacío.")
    @ApiResponse(responseCode = "401",
            description = "Falta la credencial, o es inválida o está vencida. No se cotiza nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "La credencial es válida pero no es de un administrador. No se cotiza nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409",
            description = "Ya hay un recálculo en curso, o hay una sincronización en curso de cualquiera de las "
                    + "dos fuentes. No se cotiza nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/recalculate")
    public RecalculationReportResponse recalculate() {
        return RecalculationReportResponse.from(recalculationService.recalculate(RecalculationOrigin.MANUAL));
    }

    @Tag(name = "Simulación")
    @Operation(
            summary = "Simula una estrategia sin guardar nada",
            description = "El cuerpo trae una estrategia guardada (strategyId) o parámetros (parameters); uno de los "
                    + "dos, no ambos ni ninguno. Reproduce en memoria, desde 100,00, todas las semanas cerradas que "
                    + "hoy se cotizarían y devuelve por jugador activo su cotización actual, la simulada y la "
                    + "diferencia, ordenados por cotización simulada descendente, nombre e identificador. Una "
                    + "estrategia guardada usa su versión más reciente; los parámetros se validan con las mismas "
                    + "reglas que al crear una estrategia y no se guardan. No modifica cotizaciones, estrategias ni "
                    + "la activa, y no espera a un recálculo ni a una sincronización. Los filtros y la paginación "
                    + "son los del catálogo. Cada página vuelve a calcular todo.")
    @ApiResponse(responseCode = "200", description = "Hoja de la simulación; puede estar vacía.")
    @ApiResponse(responseCode = "400",
            description = "Vienen strategyId y parameters, o ninguno; parámetros inválidos (pesos que no suman "
                    + "1,00, métrica desconocida, factor o mínimo); un filtro con valor no admitido; paginación "
                    + "fuera de rango o un cuerpo ilegible.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401",
            description = "Falta la credencial, o es inválida o está vencida. No se simula nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "La credencial es válida pero no es de un administrador. No se simula nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "La estrategia guardada no existe.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/simulate")
    public SimulationPageResponse simulate(
            @Valid @RequestBody SimulationRequest request,
            @RequestParam(required = false) League league,
            @RequestParam(required = false) @Pattern(regexp = "\\s*\\S.*") String team,
            @RequestParam(required = false) Position position,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return SimulationPageResponse.from(simulationService.simulate(request.strategyId(),
                request.simulationParameters(), new PlayerFilter(league, team, position), page, size));
    }
}
