package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * El resultado de leer el snapshot. Fuera de {@code LOADED}, el snapshot es el vacío: un archivo que
 * falta o que no se puede leer se ignora entero y se scrapea todo lo pendiente (FR-027).
 * {@code problem} explica por qué no se pudo leer.
 */
public record SnapshotRead(SnapshotStatus status, StatsSnapshot snapshot, String problem) {
    public SnapshotRead {
        if (status != SnapshotStatus.LOADED) {
            snapshot = StatsSnapshot.empty();
        }
    }

    public static SnapshotRead loaded(StatsSnapshot snapshot) {
        return new SnapshotRead(SnapshotStatus.LOADED, snapshot, null);
    }

    public static SnapshotRead missing() {
        return new SnapshotRead(SnapshotStatus.MISSING, StatsSnapshot.empty(), null);
    }

    public static SnapshotRead unreadable(String problem) {
        return new SnapshotRead(SnapshotStatus.UNREADABLE, StatsSnapshot.empty(), problem);
    }
}
