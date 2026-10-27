package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.nio.charset.StandardCharsets;

/**
 * Lo que respondió WhoScored a un pedido: el status y el cuerpo, o la falla si no hubo respuesta
 * (timeout o red). Una página fallida no tiene status ni cuerpo.
 */
public record DownloadedPage(int status, String body, String failure) {
    private static final int OK = 200;
    private static final int NO_STATUS = 0;
    private static final int BYTES_PER_KB = 1024;

    public static DownloadedPage of(int status, String body) {
        return new DownloadedPage(status, body, null);
    }

    public static DownloadedPage failed(String reason) {
        return new DownloadedPage(NO_STATUS, null, reason);
    }

    /**
     * Respondió 200 con cuerpo. Que el cuerpo traiga su bloque lo decide el extractor.
     */
    public boolean isOk() {
        return failure == null && status == OK && body != null && !body.isEmpty();
    }

    /**
     * El tamaño del cuerpo en KB, para el registro.
     */
    public long sizeInKb() {
        return body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length / BYTES_PER_KB;
    }

    /**
     * El status, o la falla si no hubo respuesta, para el registro.
     */
    public String outcome() {
        return failure != null ? failure : String.valueOf(status);
    }
}
