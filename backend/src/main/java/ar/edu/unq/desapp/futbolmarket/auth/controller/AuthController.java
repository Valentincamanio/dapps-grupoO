package ar.edu.unq.desapp.futbolmarket.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.auth.controller.dto.RegisterRequest;
import ar.edu.unq.desapp.futbolmarket.auth.controller.dto.RegisterResponse;
import ar.edu.unq.desapp.futbolmarket.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Endpoints públicos de autenticación. */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Registra un usuario",
            description = "Crea una cuenta con rol USER y el saldo inicial configurado, y devuelve la "
                    + "clave de API por única vez: no existe otra vía para volver a consultarla. El "
                    + "nombre de usuario y el correo se recortan y se comparan sin distinguir mayúsculas. "
                    + "Si el cliente declara un rol, se ignora. El registro no emite token de sesión.")
    @ApiResponse(responseCode = "201",
            description = "Usuario creado. Incluye la clave de API, que no se vuelve a mostrar.")
    @ApiResponse(responseCode = "400", description = "Los datos enviados no cumplen las reglas de formato.",
            content = @Content)
    @ApiResponse(responseCode = "409",
            description = "El nombre de usuario, el correo o ambos ya están registrados.",
            content = @Content)
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return RegisterResponse.from(
                authService.register(request.username(), request.email(), request.password()));
    }
}
