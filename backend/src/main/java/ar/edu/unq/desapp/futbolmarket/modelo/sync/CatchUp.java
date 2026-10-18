package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Qué corre la puesta al día al arrancar (research D22): las dos fuentes en cadena, solo WhoScored
 * o nada.
 */
public enum CatchUp {
    CHAIN,
    WHOSCORED_ONLY,
    NONE
}
