package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Qué disparó la sincronización: la corrida semanal, el arranque con el catálogo vacío o un
 * administrador.
 */
public enum SyncOrigin {
    WEEKLY,
    STARTUP,
    MANUAL
}
