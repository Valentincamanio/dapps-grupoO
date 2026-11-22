package ar.edu.unq.desapp.futbolmarket.service.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.config.SyncProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.CatchUpPolicy;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncFreshness;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.LastFullSyncRepository;
import lombok.RequiredArgsConstructor;

/**
 * Componente {@code sync} de {@code /actuator/health} (contracts/health-indicators.md de la 012):
 * por cada fuente, si nunca se sincronizó, si está al día o si está vencida. {@code UP} solo si las
 * dos están al día; si no, {@code UNKNOWN}; nunca {@code DOWN}. La regla del plazo es de
 * {@link CatchUpPolicy}: este indicador solo la traduce a estado y detalles.
 */
@Component
@RequiredArgsConstructor
public class SyncHealthIndicator implements HealthIndicator {

    static final String CATCH_UP_AFTER = "catchUpAfter";
    static final String SYNCHRONIZED = "synchronized";
    static final String LAST_SUCCESSFUL_FULL_SYNC = "lastSuccessfulFullSync";
    static final String STALE = "stale";
    static final String FOOTBALL_DATA_KEY = "football_data";
    static final String WHOSCORED_KEY = "whoscored";

    private static final Map<SyncSource, String> SOURCE_KEYS = Map.of(
            SyncSource.FOOTBALL_DATA, FOOTBALL_DATA_KEY,
            SyncSource.WHOSCORED, WHOSCORED_KEY);

    private final LastFullSyncRepository lastFullSyncRepository;
    private final SyncProperties syncProperties;
    private final Clock clock;

    @Override
    public Health health() {
        CatchUpPolicy policy = new CatchUpPolicy(syncProperties.catchUpAfter());
        Instant now = clock.instant();
        Health.Builder builder = Health.up().withDetail(CATCH_UP_AFTER, syncProperties.catchUpAfter().toString());
        boolean allUpToDate = true;
        for (SyncSource source : SyncSource.values()) {
            Optional<LastFullSync> last = lastFullSyncRepository.find(source);
            SyncFreshness freshness = policy.freshness(last, now);
            builder.withDetail(SOURCE_KEYS.get(source), sourceDetails(freshness, last));
            allUpToDate &= freshness == SyncFreshness.UP_TO_DATE;
        }
        return allUpToDate ? builder.build() : builder.unknown().build();
    }

    private static Map<String, Object> sourceDetails(SyncFreshness freshness, Optional<LastFullSync> last) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(SYNCHRONIZED, freshness != SyncFreshness.NEVER);
        last.ifPresent(lastFullSync -> {
            details.put(LAST_SUCCESSFUL_FULL_SYNC, lastFullSync.finishedAt().toString());
            details.put(STALE, freshness == SyncFreshness.STALE);
        });
        return details;
    }
}
