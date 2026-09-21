package ar.edu.unq.desapp.futbolmarket.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.auth.controller.dto.ApiKeyResponse;
import ar.edu.unq.desapp.futbolmarket.auth.controller.dto.ChangePasswordRequest;
import ar.edu.unq.desapp.futbolmarket.auth.controller.dto.ProfileResponse;
import ar.edu.unq.desapp.futbolmarket.auth.service.AccountService;
import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints de la cuenta propia. Todos exigen credencial.
 *
 * <p>Los dos requirements se declaran por separado a propósito: en OpenAPI eso significa que son
 * alternativas y alcanza con cualquiera de los dos (research D16). El principal es el id del
 * usuario, que arma el filtro de autenticación; así el controller no importa tipos de
 * {@code security/}.</p>
 */
@RestController
@RequestMapping("/auth/me")
@Tag(name = "Cuenta")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)
@RequiredArgsConstructor
public class AccountController {

    private static final String UNAUTHORIZED_DESCRIPTION =
            "No hay credencial, o la credencial es inválida o está vencida.";

    private final AccountService accountService;

    @Operation(
            summary = "Consulta el perfil propio",
            description = "Devuelve los datos del usuario dueño de la credencial. Nunca incluye la "
                    + "contraseña, su hash ni la clave de API.")
    @ApiResponse(responseCode = "200", description = "Perfil del usuario autenticado.")
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED_DESCRIPTION, content = @Content)
    @GetMapping
    public ProfileResponse getProfile(@AuthenticationPrincipal @Parameter(hidden = true) Long userId) {
        return ProfileResponse.from(accountService.getProfile(userId));
    }

    @Operation(
            summary = "Cambia la contraseña",
            description = "Requiere la contraseña actual y una nueva que cumpla las reglas del registro "
                    + "y sea distinta de la actual. Si el cambio se rechaza, nada se modifica. Los tokens "
                    + "de sesión ya emitidos siguen siendo válidos hasta su vencimiento. Se responde 400 y "
                    + "no 401 cuando la contraseña actual no coincide, porque el usuario sí está "
                    + "autenticado: el problema está en el cuerpo del request.")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada. Sin cuerpo.")
    @ApiResponse(responseCode = "400",
            description = "La contraseña actual no coincide, o la nueva no cumple el formato o es igual "
                    + "a la actual. La contraseña vigente no cambia.",
            content = @Content)
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED_DESCRIPTION, content = @Content)
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal @Parameter(hidden = true) Long userId,
                               @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(userId, request.currentPassword(), request.newPassword());
    }

    @Operation(
            summary = "Regenera la clave de API",
            description = "Emite una clave nueva y la devuelve por única vez. La clave anterior deja de "
                    + "ser válida en ese mismo momento: siempre hay a lo sumo una vigente. Los tokens de "
                    + "sesión ya emitidos siguen siendo válidos hasta su vencimiento. No lleva cuerpo.")
    @ApiResponse(responseCode = "200", description = "Clave nueva. No se vuelve a mostrar.")
    @ApiResponse(responseCode = "401", description = UNAUTHORIZED_DESCRIPTION, content = @Content)
    @PostMapping("/api-key")
    public ApiKeyResponse regenerateApiKey(@AuthenticationPrincipal @Parameter(hidden = true) Long userId) {
        return ApiKeyResponse.from(accountService.regenerateApiKey(userId));
    }
}
