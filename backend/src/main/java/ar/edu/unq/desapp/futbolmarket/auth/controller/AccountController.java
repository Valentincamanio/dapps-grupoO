package ar.edu.unq.desapp.futbolmarket.auth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.auth.controller.dto.ProfileResponse;
import ar.edu.unq.desapp.futbolmarket.auth.service.AccountService;
import ar.edu.unq.desapp.futbolmarket.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    private final AccountService accountService;

    @Operation(
            summary = "Consulta el perfil propio",
            description = "Devuelve los datos del usuario dueño de la credencial. Nunca incluye la "
                    + "contraseña, su hash ni la clave de API.")
    @ApiResponse(responseCode = "200", description = "Perfil del usuario autenticado.")
    @ApiResponse(responseCode = "401",
            description = "No hay credencial, o la credencial es inválida o está vencida.",
            content = @Content)
    @GetMapping
    public ProfileResponse getProfile(@AuthenticationPrincipal @Parameter(hidden = true) Long userId) {
        return ProfileResponse.from(accountService.getProfile(userId));
    }
}
