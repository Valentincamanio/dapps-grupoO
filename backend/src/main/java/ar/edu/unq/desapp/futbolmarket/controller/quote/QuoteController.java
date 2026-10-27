package ar.edu.unq.desapp.futbolmarket.controller.quote;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.CurrentQuotesResponse;
import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.PlayerQuoteResponse;
import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.QuoteHistoryPageResponse;
import ar.edu.unq.desapp.futbolmarket.controller.quote.dto.RankingPageResponse;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteQueryService;
import ar.edu.unq.desapp.futbolmarket.shared.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Las consultas públicas de cotizaciones y el ranking (research D20). No piden credencial: quedan
 * bajo la regla de {@code SecurityConfig} que deja públicas las lecturas de {@code GET /players/**}.
 *
 * <p>{@code /players/ranking} y {@code /players/quotes/current} son rutas literales, así que Spring
 * MVC las prefiere sobre {@code /players/{id}} del catálogo. Las fechas se reciben como texto para
 * que el mensaje de error repita el valor no admitido; {@code date} y {@code ids} se declaran
 * opcionales para que su falta también responda con el formato de error común.</p>
 */
@RestController
@RequestMapping("/players")
@Validated
@RequiredArgsConstructor
public class QuoteController {
    private final QuoteQueryService quoteQueryService;

    @Tag(name = "Cotizaciones")
    @Operation(summary = "Evolución de la cotización de un jugador",
            description = "Historial paginado, ordenado por vigencia ascendente, con todas las cotizaciones del "
                    + "rango (también las calculadas que una recotización reemplazó). from y to son fechas "
                    + "AAAA-MM-DD en hora de Argentina, las dos incluidas; sin ellas se devuelve todo el historial.")
    @ApiResponse(responseCode = "200", description = "Hoja del historial; puede estar vacía.")
    @ApiResponse(responseCode = "400", description = "Fecha con formato inválido, rango invertido o paginación fuera "
            + "de rango.", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No existe el jugador.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{id}/quotes")
    public QuoteHistoryPageResponse getHistory(
            @PathVariable @Min(1) Long id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return QuoteHistoryPageResponse.from(id, quoteQueryService.getHistory(id, from, to, page, size));
    }

    @Tag(name = "Cotizaciones")
    @Operation(summary = "Cotización actual de un jugador",
            description = "La última cotización del jugador, activo o inactivo.")
    @ApiResponse(responseCode = "200", description = "La cotización vigente.")
    @ApiResponse(responseCode = "400", description = "Identificador con formato inválido.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "El jugador no existe, o existe y todavía no tiene cotización; "
            + "se distinguen por el mensaje.", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{id}/quotes/current")
    public PlayerQuoteResponse getCurrent(@PathVariable @Min(1) Long id) {
        return PlayerQuoteResponse.from(quoteQueryService.getCurrent(id));
    }

    @Tag(name = "Cotizaciones")
    @Operation(summary = "Cotización de un jugador en una fecha",
            description = "La última cotización que ya regía al final de ese día, hora de Argentina.")
    @ApiResponse(responseCode = "200", description = "La cotización vigente al final de ese día.")
    @ApiResponse(responseCode = "400", description = "Fecha faltante, con formato inválido o futura.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "El jugador no existe, o no tiene cotización a esa fecha.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{id}/quotes/at")
    public PlayerQuoteResponse getAt(
            @PathVariable @Min(1) Long id,
            @Parameter(required = true, description = "Fecha AAAA-MM-DD, hora de Argentina. No puede ser futura.")
            @RequestParam(required = false) String date
    ) {
        return PlayerQuoteResponse.from(quoteQueryService.getAt(id, date));
    }

    @Tag(name = "Cotizaciones")
    @Operation(summary = "Cotización actual de varios jugadores (para la pizarra)",
            description = "Una sola consulta para los jugadores de una hoja. Los que no tienen cotización o no "
                    + "existen no figuran en la respuesta.")
    @ApiResponse(responseCode = "200", description = "La cotización actual de los jugadores que tienen una.")
    @ApiResponse(responseCode = "400", description = "Falta ids, hay más de 50 o alguno no es un entero mayor o "
            + "igual a 1.", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/quotes/current")
    public CurrentQuotesResponse getCurrentOf(
            @Parameter(required = true, description = "De 1 a 50 identificadores separados por coma.")
            @RequestParam(required = false) @NotEmpty @Size(max = QuoteQueryService.MAX_BATCH_SIZE)
            List<@NotNull @Min(1) Long> ids
    ) {
        return CurrentQuotesResponse.from(quoteQueryService.getCurrentOf(ids));
    }

    @Tag(name = "Ranking")
    @Operation(summary = "Ranking de jugadores por cotización vigente",
            description = "Los jugadores activos con cotización, por cotización descendente; ante empate, por "
                    + "nombre y por identificador. Los filtros siguen las reglas del catálogo y se combinan. "
                    + "Informa la estrategia activa.")
    @ApiResponse(responseCode = "200", description = "Hoja del ranking; puede estar vacía.")
    @ApiResponse(responseCode = "400", description = "Un filtro con valor no admitido o paginación fuera de rango.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/ranking")
    public RankingPageResponse getRanking(
            @RequestParam(required = false) League league,
            @RequestParam(required = false) @Pattern(regexp = "\\s*\\S.*") String team,
            @RequestParam(required = false) Position position,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return RankingPageResponse.from(quoteQueryService.getRanking(new PlayerFilter(league, team, position), page,
                size));
    }
}
