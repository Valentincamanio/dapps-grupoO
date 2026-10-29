package ar.edu.unq.desapp.futbolmarket.controller.market;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.market.dto.AuditPageResponse;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEventType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditFilter;
import ar.edu.unq.desapp.futbolmarket.service.market.AuditQueryService;
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
 * La auditoría del mercado (FR-053). Solo la consulta el administrador: {@code SecurityConfig}
 * responde 403 a un usuario común antes de llegar acá (research D24). No hay ninguna operación para
 * modificar ni borrar registros (FR-051).
 *
 * <p>Las fechas se reciben como texto para que el mensaje de error repita el valor no admitido, como
 * en el historial de operaciones.</p>
 */
@RestController
@RequestMapping("/audit")
@Validated
@Tag(name = "Auditoría")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
@RequiredArgsConstructor
public class AuditController {

    // Los ejemplos del contrato. Son concatenaciones y no format() porque una anotación pide constantes.
    private static final String EXAMPLE_START = "{\"timestamp\": \"2026-10-28T15:00:00Z\", ";
    private static final String AUDIT_PATH = "\"path\": \"/audit\"}";
    private static final String INVALID_PARAMETERS_EXAMPLE = EXAMPLE_START
            + "\"status\": 400, \"error\": \"Bad Request\", \"message\": \"Los parámetros enviados no son válidos.\", "
            + AUDIT_PATH;
    private static final String UNAUTHORIZED_EXAMPLE = EXAMPLE_START
            + "\"status\": 401, \"error\": \"Unauthorized\", \"message\": \"Se requiere una credencial: un token de "
            + "sesión (Authorization: Bearer) o una clave de API (X-API-Key).\", " + AUDIT_PATH;
    private static final String FORBIDDEN_EXAMPLE = EXAMPLE_START
            + "\"status\": 403, \"error\": \"Forbidden\", \"message\": \"No tiene permisos para acceder a este "
            + "recurso.\", " + AUDIT_PATH;

    private final AuditQueryService auditQueryService;

    @Operation(
            summary = "Auditoría del mercado (solo administrador)",
            description = "Cada orden creada (también las iniciales), cada operación ejecutada y cada cancelación, "
                    + "con autor, fecha y hora, detalle y el estado anterior y posterior de cada parte: saldo libre, "
                    + "saldo reservado y tenencia del jugador. De la más nueva a la más vieja, con la paginación del "
                    + "catálogo. Filtros opcionales y combinables: tipo de hecho, usuario (autor o cualquiera de las "
                    + "partes), jugador y rango de fechas AAAA-MM-DD en hora de Argentina, los dos extremos "
                    + "incluidos. Ningún registro se puede modificar ni borrar.")
    @ApiResponse(responseCode = "200", description = "Una hoja de la auditoría; puede estar vacía.")
    @ApiResponse(responseCode = "400",
            description = "Filtro o fecha no admitidos, rango con el inicio posterior al final o paginación fuera de "
                    + "rango. El mensaje de una fecha repite el valor no admitido.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = INVALID_PARAMETERS_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = "No hay credencial, o la credencial es inválida o está vencida.",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "El usuario no es administrador (antes del controller).",
            content = @Content(schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
    @GetMapping
    public AuditPageResponse getAudit(
            @RequestParam(required = false) AuditEventType eventType,
            @Parameter(description = "Autor o cualquiera de las partes del hecho.")
            @RequestParam(required = false) @Min(1) Long userId,
            @RequestParam(required = false) @Min(1) Long playerId,
            @Parameter(description = "Primer día, AAAA-MM-DD en hora de Argentina (incluido).", example = "2026-10-01")
            @RequestParam(required = false) String from,
            @Parameter(description = "Último día, AAAA-MM-DD en hora de Argentina (incluido).", example = "2026-10-31")
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        AuditFilter filter = AuditFilter.of(eventType, userId, playerId, from, to);
        return AuditPageResponse.from(auditQueryService.audit(filter, page, size));
    }
}
