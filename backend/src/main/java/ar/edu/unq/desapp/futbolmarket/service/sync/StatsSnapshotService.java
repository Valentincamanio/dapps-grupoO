package ar.edu.unq.desapp.futbolmarket.service.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.CatalogReferences;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotRead;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSnapshot;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.performance.PlayerMatchStatsRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.StatsSnapshotRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import lombok.RequiredArgsConstructor;

/**
 * Lee el snapshot para la sincronización y lo exporta desde la base (research D20 y D21).
 *
 * <p>Solo lee equipos, partidos y estadísticas: nunca usuarios, contraseñas, claves ni operaciones
 * (FR-029).</p>
 */
@Service
@RequiredArgsConstructor
public class StatsSnapshotService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatsSnapshotService.class);

    private final StatsSnapshotRepository snapshotRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatsRepository statsRepository;

    public SnapshotRead read() {
        return snapshotRepository.read();
    }

    /**
     * Los equipos y partidos del catálogo, de cualquier temporada, que nombra el snapshot de una liga.
     * Distinguen lo que no está en el catálogo de lo que es de otra temporada.
     */
    public CatalogReferences references(StatsSnapshot forLeague) {
        return new CatalogReferences(
                teamRepository.findAllByExternalIds(forLeague.teamExternalIds()),
                matchRepository.findAllByExternalIds(forLeague.matchExternalIds()));
    }

    /**
     * Combina el snapshot vigente con lo que hay en la base y lo escribe (FR-030 y FR-031). Un vigente
     * que no se puede leer no se pisa: la exportación devuelve {@code false}.
     */
    public boolean export() {
        SnapshotRead current = snapshotRepository.read();
        if (current.status() == SnapshotStatus.UNREADABLE) {
            LOGGER.error("No se exporta el snapshot: el vigente no se puede leer ({}) y no se pisa.",
                    current.problem());
            return false;
        }
        StatsSnapshot fromDatabase = StatsSnapshot.of(teamRepository.findAllCrossed(), matchRepository.findAllCrossed(),
                matchRepository.findAllWithStats(), statsRepository.findAll());
        StatsSnapshot merged = current.snapshot().merge(fromDatabase);
        snapshotRepository.write(merged);
        LOGGER.info("Snapshot exportado: {} equipos, {} partidos y {} partidos con estadísticas.",
                merged.teams().size(), merged.matches().size(), merged.stats().size());
        return true;
    }
}
