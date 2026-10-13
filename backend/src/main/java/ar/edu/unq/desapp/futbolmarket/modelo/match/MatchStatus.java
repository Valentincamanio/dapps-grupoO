package ar.edu.unq.desapp.futbolmarket.modelo.match;

/**
 * Estado de un partido, con los mismos valores que informa la fuente (FR-023).
 */
public enum MatchStatus {
    SCHEDULED,
    TIMED,
    IN_PLAY,
    PAUSED,
    EXTRA_TIME,
    PENALTY_SHOOTOUT,
    FINISHED,
    SUSPENDED,
    POSTPONED,
    CANCELLED,
    AWARDED
}
