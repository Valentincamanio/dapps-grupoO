package ar.edu.unq.desapp.futbolmarket.shared;

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.security.core.Authentication;

/**
 * Los datos crudos de una invocación a un endpoint propio, tal como los junta el aspecto de
 * auditoría de web services.
 *
 * <p>Desacopla al formatter de AspectJ y del servlet: el formatter recibe solo este record, así
 * se testea sin Spring, sin join points y sin request (data-model de la 011).</p>
 *
 * @param authentication la autenticación del contexto de seguridad; {@code null} si no hay
 * @param controllerType la clase real del controller, no la del proxy
 * @param method el método invocado
 * @param arguments los argumentos, en el orden de declaración; admite elementos {@code null}
 * @param httpMethod el verbo HTTP; {@code null} si no hay request en el hilo
 * @param requestUri la URI pedida, sin query string; {@code null} si no hay request en el hilo
 * @param elapsedNanos lo que tardó la invocación, sin contar la auditoría
 * @param failure la excepción con la que terminó; {@code null} si volvió o si salió un {@code Error}
 * @param completed {@code true} si el método volvió sin lanzar nada
 */
public record WebServiceInvocation(
        Authentication authentication,
        Class<?> controllerType,
        Method method,
        List<Object> arguments,
        String httpMethod,
        String requestUri,
        long elapsedNanos,
        RuntimeException failure,
        boolean completed
) {
}
