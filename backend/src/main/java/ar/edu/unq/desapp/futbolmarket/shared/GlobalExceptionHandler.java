package ar.edu.unq.desapp.futbolmarket.shared;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String BAD_REQUEST = "Solicitud inválida";
    private static final String NOT_FOUND = "No encontrado";

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<ApiError> handlePlayerNotFound(
            PlayerNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, NOT_FOUND, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, BAD_REQUEST, "Los parámetros enviados no son válidos.", request, List.of());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request,
            List<ApiError.Violation> violations
    ) {
        var body = new ApiError(Instant.now(), status.value(), error, message, request.getRequestURI(), violations);
        return ResponseEntity.status(status).body(body);
    }
}
