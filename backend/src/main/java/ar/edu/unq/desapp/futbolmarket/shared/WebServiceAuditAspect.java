package ar.edu.unq.desapp.futbolmarket.shared;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Auditoría de web services: cada invocación a un endpoint propio deja exactamente una línea en el
 * logger {@code WEB_SERVICE_AUDIT}, con el usuario, la operación, los parámetros, el tiempo y el
 * resultado (research D2, D5, D7 y D11 de la 011).
 *
 * <p>Audita los métodos públicos de las clases {@code @RestController} del paquete
 * {@code controller}, terminen bien o con una excepción. No ve lo que se rechaza antes de llegar al
 * endpoint: los 401 y 403 del filtro de seguridad, los 400 que Spring MVC lanza al resolver los
 * argumentos (cuerpo ilegible, {@code @Valid} del cuerpo, conversión de tipos), las rutas sin
 * handler, el actuator, Swagger, la consola de H2 ni los procesos programados (research D12).</p>
 *
 * <p>Se captura {@code RuntimeException} y no {@code Throwable}: ningún endpoint declara
 * excepciones checked, así que eso cubre todo lo que puede salir de un controller salvo un
 * {@code Error} de la JVM. La excepción se registra y se relanza intacta, la misma instancia, así
 * la respuesta no cambia. Si sale un {@code Error}, el {@code finally} igual escribe la línea, con
 * {@code result=ERROR} sin tipo. El {@code throws Throwable} lo impone
 * {@link ProceedingJoinPoint#proceed()}.</p>
 *
 * <p>El tiempo se mide antes de armar la línea, así no incluye la serialización. Cada invocación
 * hace una sola llamada al logger, así las líneas de requests simultáneos no se mezclan. Con el
 * logger apagado ({@code logging.level.WEB_SERVICE_AUDIT=OFF}) la línea ni siquiera se arma.</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class WebServiceAuditAspect {

    public static final String WEB_SERVICE_AUDIT_LOGGER = "WEB_SERVICE_AUDIT";

    private static final String CONTROLLER_ENDPOINTS =
            "execution(public * ar.edu.unq.desapp.futbolmarket.controller..*.*(..))"
                    + " && @within(org.springframework.web.bind.annotation.RestController)";
    private static final String MESSAGE = "user={} | op={} {} {} | params={} | time={}ms | result={}";
    private static final Logger AUDIT = LoggerFactory.getLogger(WEB_SERVICE_AUDIT_LOGGER);

    private final WebServiceAuditFormatter formatter;

    @Around(CONTROLLER_ENDPOINTS)
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        boolean completed = false;
        RuntimeException failure = null;
        try {
            Object result = joinPoint.proceed();
            completed = true;
            return result;
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            long elapsedNanos = System.nanoTime() - start;
            write(joinPoint, elapsedNanos, failure, completed);
        }
    }

    private void write(ProceedingJoinPoint joinPoint, long elapsedNanos, RuntimeException failure, boolean completed) {
        if (!AUDIT.isInfoEnabled()) {
            return;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        HttpServletRequest request = currentRequest();
        WebServiceInvocation invocation = new WebServiceInvocation(
                SecurityContextHolder.getContext().getAuthentication(),
                signature.getDeclaringType(),
                signature.getMethod(),
                Arrays.asList(joinPoint.getArgs()),
                request == null ? null : request.getMethod(),
                request == null ? null : request.getRequestURI(),
                elapsedNanos,
                failure,
                completed);
        AUDIT.info(MESSAGE,
                formatter.user(invocation.authentication()),
                formatter.operation(invocation),
                formatter.httpMethod(invocation),
                formatter.requestUri(invocation),
                formatter.params(invocation),
                formatter.elapsedMillis(invocation),
                formatter.result(invocation));
    }

    /** El request del hilo, o {@code null} si la invocación no viene de Spring MVC. */
    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes ? servletAttributes.getRequest() : null;
    }
}
