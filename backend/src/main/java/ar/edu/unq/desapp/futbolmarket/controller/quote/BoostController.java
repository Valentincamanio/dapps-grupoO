package ar.edu.unq.desapp.futbolmarket.controller.quote;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.BoostWeekRequest;
import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.BoostedWeekResponse;
import ar.edu.unq.desapp.futbolmarket.service.quote.BoostService;
import ar.edu.unq.desapp.futbolmarket.shared.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Los jugadores potenciados de cada semana. Ver los de la semana en curso es público, como el resto
 * de {@code GET /players/**}; consultar cualquier semana y elegirlos es de administradores: la regla
 * está en {@code SecurityConfig} ({@code /quotes/boosts/**}), así que un 401 o un 403 ocurre antes
 * de llegar acá y no cambia nada.
 */
@RestController
@Tag(name = "Potenciados")
@Validated
@RequiredArgsConstructor
public class BoostController {

    private static final String WEEK_DESCRIPTION = "Las semanas van de lunes a domingo en hora de Argentina; "
            + "cualquier día (AAAA-MM-DD) indica la semana que lo contiene.";
    private static final String UNAUTHORIZED = "Falta la credencial, o es inválida o está vencida. No cambia nada.";
    private static final String FORBIDDEN = "La credencial es válida pero no es de un administrador. No cambia nada.";

    private final BoostService boostService;

    @Operation(summary = "Los potenciados de la semana en curso",
            description = "Público. Los jugadores que el administrador potenció para esta semana, con su porcentaje: "
                    + "en el cierre de la semana su factor de escala se multiplica por ese porcentaje. La cotización "
                    + "vigente todavía no lo lleva. Si la semana no tiene potenciados, players viene vacía.")
    @ApiResponse(responseCode = "200", description = "La semana en curso y sus potenciados, que pueden ser ninguno.")
    @GetMapping("/players/boosts/current")
    public BoostedWeekResponse current() {
        return BoostedWeekResponse.from(boostService.current());
    }

    @Operation(summary = "Los potenciados de una semana",
            description = "Los de cualquier semana, cerrada, en curso o próxima. " + WEEK_DESCRIPTION
                    + " Sin week, la semana en curso.")
    @ApiResponse(responseCode = "200", description = "La semana, su estado y sus potenciados, que pueden ser ninguno.")
    @ApiResponse(responseCode = "400", description = "Fecha con formato inválido.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
    @GetMapping("/quotes/boosts")
    public BoostedWeekResponse week(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return BoostedWeekResponse.from(week == null ? boostService.current() : boostService.week(week));
    }

    @Operation(summary = "Elige los potenciados de una semana",
            description = "Reemplaza los potenciados de la semana: exactamente cuatro jugadores distintos, cada uno "
                    + "con un porcentaje de 200 a 400 de a 25, o una lista vacía para dejarla sin potenciados. "
                    + "Se puede cambiar la semana en curso y las próximas, nunca una cerrada. No recotiza nada: "
                    + "rigen en el cierre de la semana, cuando se calcula su cotización, y en cualquier recotización "
                    + "que se haga mientras la semana está en curso (editar o activar la estrategia con AHORA). "
                    + WEEK_DESCRIPTION)
    @ApiResponse(responseCode = "200", description = "La semana con los potenciados guardados.")
    @ApiResponse(responseCode = "400", description = "Fecha o cuerpo inválidos, una cantidad distinta de cuatro "
            + "(salvo ninguno), un jugador repetido o un porcentaje fuera de rango. No se guarda nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = FORBIDDEN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Un jugador no existe. No se guarda nada.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "La semana ya cerró, o otro cambio de la misma semana se cruzó "
            + "con este. No se guarda nada.", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
    @PutMapping("/quotes/boosts/{week}")
    public BoostedWeekResponse replace(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                                       @Valid @RequestBody BoostWeekRequest request) {
        return BoostedWeekResponse.from(boostService.replace(week, request.boosts()));
    }
}
