package ar.edu.unq.desapp.futbolmarket.shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INVALID_PARAMETERS = "Los parámetros enviados no son válidos.";
    private static final String INVALID_REQUEST = "El request tiene datos inválidos.";
    private static final String UNREADABLE_BODY = "El cuerpo del request no es un JSON válido.";
    private static final String UNEXPECTED_ERROR = "Ocurrió un error inesperado.";

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequestException(BadRequestException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorizedException(UnauthorizedException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), request, null);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFoundException(NotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflictException(ConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    /** Los mensajes de cada violación ya vienen en español desde las anotaciones de los DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiError.Violation(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, INVALID_REQUEST, request, violations);
    }

    /** No repite el cuerpo recibido: puede traer una contraseña. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, UNREADABLE_BODY, request, null);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, INVALID_PARAMETERS, request, null);
    }

    /**
     * Responde 500 sin el mensaje de la excepción ni el stack trace; el detalle queda del lado
     * del servidor.
     *
     * <p>Relanza las que no le corresponden: las de la cadena de filtros, que resuelven el entry
     * point y el access denied handler de {@code security/}, y las que Spring MVC ya sabe
     * traducir a su propio status, como una ruta inexistente o un método no soportado.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) throws Exception {
        if (exception instanceof AuthenticationException
                || exception instanceof AccessDeniedException
                || exception instanceof ErrorResponse) {
            throw exception;
        }
        LOGGER.error("Error inesperado al procesar {}", request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR, request, null);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            List<ApiError.Violation> violations
    ) {
        var body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                violations
        );
        return ResponseEntity.status(status).body(body);
    }
}
