package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Por qué un partido pendiente no se procesó. El partido sigue pendiente y va al informe
 * (research D13).
 */
public enum StatsSkipReason {
    UNCROSSED_TEAM,
    NOT_IN_SOURCE,
    NOT_FINISHED_IN_SOURCE,
    INVALID_IN_SOURCE,
    WRITE_FAILED
}
