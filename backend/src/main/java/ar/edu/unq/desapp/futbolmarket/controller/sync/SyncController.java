package ar.edu.unq.desapp.futbolmarket.controller.sync;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.sync.dto.SyncReportResponse;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.service.sync.SyncService;
import ar.edu.unq.desapp.futbolmarket.shared.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Disparo manual de la sincronización con Football-Data.org. Solo un administrador puede usarlo:
 * la regla está en {@code SecurityConfig}, así que un rechazo ocurre antes de llegar acá y nunca
 * se consulta la fuente (FR-029).
 *
 * <p>La ruta va bajo {@code /players} porque lo que el cliente ve es el catálogo (research D15).
 * Los dos requirements son alternativas, igual que en {@code AccountController}.</p>
 */
@RestController
@RequestMapping("/players/sync")
@Tag(name = "Sincronización")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @Operation(
            summary = "Sincroniza el catálogo con Football-Data.org",
            description = "Sincroniza las cinco ligas. El request espera a que la sincronización termine y "
                    + "devuelve su informe con 200, aunque alguna liga, o todas, haya fallado. Una liga que "
                    + "falla conserva exactamente lo que tenía. Solo una sincronización completa con las cinco "
                    + "ligas en éxito inactiva a los jugadores que no aparecieron en ningún plantel: "
                    + "inactivationApplied lo indica. Hay a lo sumo una sincronización a la vez, sea manual, "
                    + "semanal o de arranque. La respuesta de la fuente puede demorar el request hasta un "
                    + "minuto por el límite de consultas del plan gratis.")
    @ApiResponse(responseCode = "200", description = "Sincronización terminada. Devuelve el informe.")
    @ApiResponse(responseCode = "401",
            description = "Falta la credencial, o es inválida o está vencida. No se consulta la fuente.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "La credencial es válida pero no es de un administrador. No se consulta la fuente.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503",
            description = "La sincronización está deshabilitada porque falta la credencial de la fuente.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    public SyncReportResponse synchronize() {
        return SyncReportResponse.from(syncService.synchronizeAll(SyncOrigin.MANUAL));
    }
}
