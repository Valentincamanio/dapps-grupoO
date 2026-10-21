package ar.edu.unq.desapp.futbolmarket.modelo.sync.exception;

/**
 * Se interrumpió la espera entre dos pedidos a WhoScored, por ejemplo porque la aplicación se está
 * apagando. Termina la corrida.
 *
 * <p>Nunca llega a una respuesta HTTP. Quien la lanza ya restauró la marca de interrupción del
 * hilo.</p>
 */
public class SyncInterruptedException extends RuntimeException {
    private static final String MESSAGE = "Se interrumpió la espera entre pedidos a WhoScored.";

    public SyncInterruptedException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
