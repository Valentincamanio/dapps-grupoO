package ar.edu.unq.desapp.futbolmarket.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.futbolmarket.auth.controller.dto.LoginRequest;
import ar.edu.unq.desapp.futbolmarket.auth.controller.dto.LoginResponse;
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

    @Operation(
            summary = "Inicia sesión",
            description = "Valida el nombre de usuario, sin distinguir mayúsculas, y la contraseña, "
                    + "y devuelve un token de sesión con su vencimiento. Si el usuario no existe o la "
                    + "contraseña es incorrecta, el rechazo es idéntico en los dos casos.")
    @ApiResponse(responseCode = "200",
            description = "Credenciales correctas. Devuelve el token de sesión y su vencimiento.")
    @ApiResponse(responseCode = "400", description = "Falta el nombre de usuario o la contraseña.",
            content = @Content)
    @ApiResponse(responseCode = "401", description = "Usuario inexistente o contraseña incorrecta.",
            content = @Content)
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authService.login(request.username(), request.password()));
    }
}
