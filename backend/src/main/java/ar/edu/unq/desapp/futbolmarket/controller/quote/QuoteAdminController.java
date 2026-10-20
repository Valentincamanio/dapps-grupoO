package ar.edu.unq.desapp.futbolmarket.controller.quote;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.RecalculationReportResponse;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.RecalculationOrigin;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteRecalculationService;
import ar.edu.unq.desapp.futbolmarket.shared.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Disparo manual del recálculo de las cotizaciones. Solo un administrador puede usarlo: la regla
 * está en {@code SecurityConfig} ({@code POST /quotes/**}), así que un rechazo ocurre antes de
 * llegar acá y no se cotiza nada (FR-053).
 *
 * <p>Los dos requirements son alternativas, igual que en {@code SyncController}.</p>
 */
@RestController
@RequestMapping("/quotes")
@Tag(name = "Recálculo")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
@RequiredArgsConstructor
public class QuoteAdminController {

    private final QuoteRecalculationService recalculationService;

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
}
