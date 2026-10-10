package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import java.time.Instant;

public record SkippedMatch(String externalId, String homeTeamExternalId, String awayTeamExternalId, Instant utcDate,
                           MatchSkipReason reason) {
}
