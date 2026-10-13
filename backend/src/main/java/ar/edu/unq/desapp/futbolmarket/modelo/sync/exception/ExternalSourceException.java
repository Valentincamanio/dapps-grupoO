package ar.edu.unq.desapp.futbolmarket.modelo.sync.exception;

/**
 * Una liga no se pudo traer de la fuente o lo que trajo no se puede usar. Su mensaje es el motivo
 * que va al informe, siempre una constante en español: así nunca lleva el token ni datos del
 * proveedor (research D5).
 *
 * <p>No depende del proveedor y nunca llega a una respuesta HTTP: el orquestador la captura por
 * liga y marca esa liga como fallida.</p>
 */
public class ExternalSourceException extends RuntimeException {
    public static final String INVALID_FORMAT = "La respuesta de la fuente no tiene el formato esperado.";
    public static final String NO_TEAMS = "La fuente no informó ningún equipo para la liga.";
    public static final String MATCHES_FROM_OTHER_SEASON = "La fuente informó partidos de otra temporada.";

    public ExternalSourceException(String reason) {
        super(reason);
    }

    /**
     * Conserva la excepción original solo como causa, para el stack trace del registro.
     */
    public ExternalSourceException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
