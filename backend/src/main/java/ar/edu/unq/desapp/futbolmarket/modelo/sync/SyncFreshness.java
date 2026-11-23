package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Qué tan reciente es la última sincronización completa exitosa de una fuente, según el plazo de
 * {@link CatchUpPolicy} (research D8 de la 012).
 */
public enum SyncFreshness {
    /** Nunca hubo una sincronización completa exitosa de la fuente. */
    NEVER,
    /** La última completa terminó hace a lo sumo el plazo: está al día. */
    UP_TO_DATE,
    /** La última completa terminó hace más que el plazo: está vencida. */
    STALE
}
