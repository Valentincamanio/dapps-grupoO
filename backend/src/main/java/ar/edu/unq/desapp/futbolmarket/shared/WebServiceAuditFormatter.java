package ar.edu.unq.desapp.futbolmarket.shared;

import java.lang.reflect.Parameter;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Arma el texto de cada campo de la línea de auditoría de web services a partir de los datos
 * crudos de una invocación ({@link WebServiceInvocation}). No tiene estado y no depende de AspectJ
 * ni del servlet, así se testea con {@code new} y sin contexto de Spring.
 *
 * <ul>
 *   <li>{@code user}: el principal con su rol y su tipo de credencial, o {@code anonymous}
 *   (FR-006, research D6).</li>
 *   <li>{@code op}: controller y método, más el verbo y la URI del request, con los caracteres de
 *   control escapados (FR-007, FR-018, research D7).</li>
 *   <li>{@code params}: un objeto JSON nombre → valor con los argumentos del método, serializados
 *   uno por uno con el {@code JsonMapper} de la app, nunca con {@code toString()}; los secretos se
 *   enmascaran, la identidad del usuario no se repite y los valores largos y el total se truncan
 *   (FR-008, FR-013, FR-015, FR-017, FR-019, FR-021, research D8 a D10).</li>
 *   <li>{@code time} y {@code result}: cuánto tardó y cómo terminó (FR-009, FR-010, research
 *   D11).</li>
 * </ul>
 *
 * <p>Un secreto es toda propiedad, o parámetro de primer nivel, cuyo nombre en minúsculas y sin
 * {@code _} ni {@code -} contiene {@code password}, {@code token}, {@code apikey} o
 * {@code secret}, en cualquier nivel del árbol. Su valor se reemplaza por {@code ***} sea del tipo
 * que sea, antes de truncar. Riesgo aceptado (research D9): un campo de negocio con
 * {@code token} en el nombre también sale enmascarado; se pierde un dato en el log, pero no se
 * filtra un secreto.</p>
 *
 * <p>Ningún método lanza excepciones: lo único que puede fallar es la serialización de un
 * argumento, y ese argumento queda con el marcador {@code <no representable>}.</p>
 */
@Component
@RequiredArgsConstructor
public class WebServiceAuditFormatter {

    private static final String ANONYMOUS = "anonymous";
    private static final String UNKNOWN_ROLE = "?";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String USER_WITH_CREDENTIAL = "%s(%s,%s)";
    private static final String USER_WITHOUT_CREDENTIAL = "%s(%s)";
    private static final String OPERATION = "%s.%s";
    private static final String NO_REQUEST = "-";
    private static final String UNREPRESENTABLE = "<no representable>";
    private static final String TRUNCATION_MARK = "...(truncado)";
    private static final int MAX_VALUE_LENGTH = 500;
    private static final int MAX_PARAMS_LENGTH = 2000;
    private static final String OK = "OK";
    private static final String ERROR = "ERROR";
    private static final String ERROR_WITH_TYPE = "ERROR(%s)";
    private static final char FIRST_PRINTABLE = ' ';
    private static final char DELETE = 0x7F;
    private static final String UNICODE_ESCAPE = "\\u%04X";
    private static final String MASK = "***";
    private static final List<String> SECRET_NAME_FRAGMENTS = List.of("password", "token", "apikey", "secret");

    /** Argumentos que no son datos de la invocación, o que son la identidad, que va solo en {@code user}. */
    private static final List<Class<?>> EXCLUDED_TYPES = List.of(
            ServletRequest.class,
            ServletResponse.class,
            Errors.class,
            Principal.class);

    private final JsonMapper jsonMapper;

    /**
     * {@code <id>(<ROL>,<CREDENCIAL>)}, {@code <id>(<ROL>)} si la autenticación no dice con qué
     * credencial se resolvió, o {@code anonymous}.
     */
    public String user(Authentication authentication) {
        if (isAnonymous(authentication)) {
            return ANONYMOUS;
        }
        String role = role(authentication);
        if (authentication.getDetails() instanceof CredentialType credential) {
            return USER_WITH_CREDENTIAL.formatted(authentication.getName(), role, credential.name());
        }
        return USER_WITHOUT_CREDENTIAL.formatted(authentication.getName(), role);
    }

    /** {@code <Controller>.<método>}, con el nombre simple de la clase real del controller. */
    public String operation(WebServiceInvocation invocation) {
        return OPERATION.formatted(invocation.controllerType().getSimpleName(), invocation.method().getName());
    }

    public String httpMethod(WebServiceInvocation invocation) {
        return requestField(invocation.httpMethod());
    }

    public String requestUri(WebServiceInvocation invocation) {
        return requestField(invocation.requestUri());
    }

    /**
     * Los argumentos del método como un objeto JSON en una línea, con el nombre de cada parámetro.
     * Cada argumento se convierte por separado: si uno no se puede serializar, ese queda con el
     * marcador y el resto se escribe igual. Sin argumentos que registrar, {@code {}}.
     */
    public String params(WebServiceInvocation invocation) {
        ObjectNode params = jsonMapper.createObjectNode();
        Parameter[] parameters = invocation.method().getParameters();
        List<Object> arguments = invocation.arguments();
        for (int index = 0; index < parameters.length && index < arguments.size(); index++) {
            Parameter parameter = parameters[index];
            if (isRecorded(parameter)) {
                String name = parameter.getName();
                params.set(name, isSecret(name) ? stringNode(MASK) : value(arguments.get(index)));
            }
        }
        return truncate(jsonMapper.writeValueAsString(params), MAX_PARAMS_LENGTH);
    }

    public long elapsedMillis(WebServiceInvocation invocation) {
        return TimeUnit.NANOSECONDS.toMillis(invocation.elapsedNanos());
    }

    /**
     * {@code OK} si volvió; {@code ERROR(<NombreSimple>)} si salió una excepción; {@code ERROR}
     * solo si no volvió y no hay excepción, porque salió un {@code Error} de la JVM. Nunca el
     * mensaje de la excepción: puede traer datos del request (FR-010).
     */
    public String result(WebServiceInvocation invocation) {
        if (invocation.completed()) {
            return OK;
        }
        RuntimeException failure = invocation.failure();
        return failure == null ? ERROR : ERROR_WITH_TYPE.formatted(failure.getClass().getSimpleName());
    }

    private static boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
    }

    /** La primera authority {@code ROLE_*}, sin el prefijo. */
    private static String role(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .findFirst()
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .orElse(UNKNOWN_ROLE);
    }

    private static String requestField(String value) {
        return value == null ? NO_REQUEST : escapeControlCharacters(value);
    }

    /** Ni el principal ({@code @AuthenticationPrincipal}, también como meta-anotación) ni los tipos excluidos. */
    private static boolean isRecorded(Parameter parameter) {
        return !AnnotatedElementUtils.hasAnnotation(parameter, AuthenticationPrincipal.class)
                && EXCLUDED_TYPES.stream().noneMatch(type -> type.isAssignableFrom(parameter.getType()));
    }

    private static boolean isSecret(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return SECRET_NAME_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    private JsonNode value(Object argument) {
        if (argument == null) {
            return jsonMapper.nullNode();
        }
        try {
            JsonNode tree = jsonMapper.valueToTree(argument);
            return sanitize(tree);
        } catch (JacksonException e) {
            return stringNode(UNREPRESENTABLE);
        }
    }

    /**
     * Recorre el árbol: enmascara las propiedades secretas y trunca cada string, en objetos
     * anidados y en arrays. Una propiedad secreta no se recorre: se reemplaza entera.
     */
    private JsonNode sanitize(JsonNode node) {
        if (node.isObject()) {
            return sanitizeObject((ObjectNode) node);
        }
        if (node.isArray()) {
            return sanitizeArray((ArrayNode) node);
        }
        if (node.isString()) {
            return sanitizeString(node);
        }
        return node;
    }

    private ObjectNode sanitizeObject(ObjectNode object) {
        ObjectNode sanitized = jsonMapper.createObjectNode();
        object.properties().forEach(property -> sanitized.set(property.getKey(),
                isSecret(property.getKey()) ? stringNode(MASK) : sanitize(property.getValue())));
        return sanitized;
    }

    private ArrayNode sanitizeArray(ArrayNode array) {
        ArrayNode sanitized = jsonMapper.createArrayNode();
        array.values().forEach(element -> sanitized.add(sanitize(element)));
        return sanitized;
    }

    private JsonNode sanitizeString(JsonNode string) {
        String value = string.asString();
        return value.length() > MAX_VALUE_LENGTH ? stringNode(truncate(value, MAX_VALUE_LENGTH)) : string;
    }

    private JsonNode stringNode(String value) {
        return jsonMapper.getNodeFactory().stringNode(value);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) + TRUNCATION_MARK : value;
    }

    /** Así un valor del request no puede partir la línea ni simular otra (FR-018). */
    private static String escapeControlCharacters(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        value.chars().forEach(character -> escaped.append(escape((char) character)));
        return escaped.toString();
    }

    private static String escape(char character) {
        return switch (character) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> isControl(character) ? UNICODE_ESCAPE.formatted((int) character) : String.valueOf(character);
        };
    }

    private static boolean isControl(char character) {
        return character < FIRST_PRINTABLE || character == DELETE;
    }
}
