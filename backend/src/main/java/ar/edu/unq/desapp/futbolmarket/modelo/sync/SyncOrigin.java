package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Qué disparó la sincronización: la cadena del lunes, la puesta al día al arrancar o un
 * administrador. {@code CATCH_UP} reemplaza a {@code STARTUP} de la 005 (FR-053).
 */
public enum SyncOrigin {
    WEEKLY,
    CATCH_UP,
    MANUAL
}
