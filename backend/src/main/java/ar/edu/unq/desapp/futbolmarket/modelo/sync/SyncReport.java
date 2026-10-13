package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

/**
 * El informe de una sincronización (FR-043 y FR-044). Va al registro y a la respuesta del disparo
 * manual; no se guarda. Las ligas van en el orden del enum, una por liga pedida.
 */
public record SyncReport(SyncType type, SyncOrigin origin, Instant startedAt, Instant finishedAt,
                         List<LeagueSyncResult> leagues, List<DuplicatedPlayer> duplicatedPlayers,
                         List<Player> inactivatedPlayers, boolean inactivationApplied) {
    public SyncReport {
        leagues = List.copyOf(leagues);
        duplicatedPlayers = List.copyOf(duplicatedPlayers);
        inactivatedPlayers = List.copyOf(inactivatedPlayers);
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

    public List<LeagueSyncResult> failedLeagues() {
        return leagues.stream().filter(league -> !league.succeeded()).toList();
    }

    /**
     * Por qué no se inactivó a nadie, o vacío si se aplicó la inactivación. La decisión queda en el
     * modelo y el registro solo la escribe (Principio II).
     */
    public Optional<InactivationSkipReason> inactivationSkipReason() {
        if (inactivationApplied) {
            return Optional.empty();
        }
        if (type == SyncType.SINGLE_LEAGUE) {
            return Optional.of(InactivationSkipReason.SINGLE_LEAGUE);
        }
        return Optional.of(InactivationSkipReason.FAILED_LEAGUES);
    }
}
