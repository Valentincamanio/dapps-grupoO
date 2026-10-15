package ar.edu.unq.desapp.futbolmarket.controller.sync;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.sync.dto.StatsSyncReportResponse;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.service.sync.StatsSyncService;
import ar.edu.unq.desapp.futbolmarket.shared.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Disparo manual de la sincronización con WhoScored. Solo un administrador puede usarlo: la regla
 * está en {@code SecurityConfig}, así que un rechazo ocurre antes de llegar acá y nunca se consulta
 * WhoScored (research D24).
 *
 * <p>Solo habla con {@link StatsSyncService}: no dispara la sincronización con Football-Data
 * (FR-038). Los dos requirements son alternativas, igual que en {@code SyncController}.</p>
 */
@RestController
@RequestMapping("/matches/stats/sync")
@Tag(name = "Sincronización")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
@RequiredArgsConstructor
public class StatsSyncController {

    private final StatsSyncService statsSyncService;

    @Operation(
            summary = "Sincroniza con WhoScored las estadísticas de los partidos terminados",
            description = "Sin league, procesa las cinco ligas. Con league, solo esa liga. No necesita la "
                    + "credencial de Football-Data: trabaja sobre los partidos ya guardados. Primero completa "
                    + "desde el snapshot versionado los partidos pendientes que este trae, sin consultar "
                    + "WhoScored. Después scrapea los partidos terminados que siguen sin estadísticas, una página "
                    + "a la vez y con al menos 4 segundos entre una y la siguiente. El request espera a que la "
                    + "sincronización termine y devuelve su informe con 200. También cuando WhoScored bloqueó: "
                    + "en ese caso cut dice en qué página se cortó, lo ya guardado se conserva y lo que falta "
                    + "queda pendiente para la próxima. Sin snapshot y con la temporada avanzada puede tardar "
                    + "unos 47 minutos.")
    @ApiResponse(responseCode = "200",
            description = "Sincronización terminada, completa o cortada por bloqueo. Devuelve el informe.")
    @ApiResponse(responseCode = "400",
            description = "La liga pedida no es una de las cinco. No se consulta WhoScored.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401",
            description = "Falta la credencial, o es inválida o está vencida. No se consulta ninguna fuente.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "La credencial es válida pero no es de un administrador. No se consulta ninguna fuente.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409",
            description = "Ya hay una sincronización en curso, de cualquiera de las dos fuentes: un disparo "
                    + "manual, la cadena semanal o la puesta al día al arrancar.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    public StatsSyncReportResponse synchronize(
            @Parameter(description = "Liga a sincronizar. Sin este parámetro se sincronizan las cinco. Un valor "
                    + "que no es una de las cinco, vacío o en minúsculas se rechaza con 400 indicando el valor "
                    + "recibido, sin consultar la fuente.", schema = @Schema(implementation = League.class))
            @RequestParam(required = false) String league) {
        StatsSyncReport report = league == null
                ? statsSyncService.synchronizeAll(SyncOrigin.MANUAL)
                : statsSyncService.synchronizeLeague(League.fromName(league));
        return StatsSyncReportResponse.from(report);
    }
}
