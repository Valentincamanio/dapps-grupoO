package ar.edu.unq.desapp.futbolmarket.service.sync;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.adapter.footballdata.FootballDataAdapter;
import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncRun;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncDisabledException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import lombok.RequiredArgsConstructor;

/**
 * Orquesta una sincronización con Football-Data.org (research D8 y D11): descarga todas las ligas
 * pedidas, resuelve los jugadores informados en dos planteles, escribe cada liga en su propia
 * transacción, inactiva si corresponde y registra el informe.
 *
 * <p>No es {@code @Transactional} a propósito: si una liga falla al escribir, su transacción se
 * revierte sola y no deja nada marcado como rollback-only para las demás (FR-034 y FR-035). Por
 * liga se capturan solo las fallas de la fuente y de la base; un error de programación aborta la
 * corrida.</p>
 *
 * <p>Hay a lo sumo una sincronización a la vez. Primero se revisa que esté habilitada (503) y
 * después que no haya otra en curso (409). El semáforo no espera: un segundo pedido se rechaza en
 * lugar de encolarse.</p>
 */
@Service
@RequiredArgsConstructor
public class SyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncService.class);
    private static final String WRITE_FAILURE_REASON = "No se pudieron guardar los datos de la liga.";

    private final FootballDataProperties properties;
    private final FootballDataAdapter adapter;
    private final SyncWriteService writeService;
    private final PlayerRepository playerRepository;
    private final SyncReportLogger reportLogger;
    private final Clock clock;
    private final Semaphore running = new Semaphore(1);

    public SyncReport synchronizeAll(SyncOrigin origin) {
        return synchronize(() -> SyncRun.full(origin, clock.instant()));
    }

    public SyncReport synchronizeLeague(League league) {
        return synchronize(() -> SyncRun.singleLeague(league, clock.instant()));
    }

    /**
     * La corrida se crea recién con el semáforo tomado, así su inicio es el de la sincronización.
     */
    private SyncReport synchronize(Supplier<SyncRun> newRun) {
        if (!properties.hasToken()) {
            throw new SyncDisabledException();
        }
        if (!running.tryAcquire()) {
            throw new SyncInProgressException();
        }
        try {
            return execute(newRun.get());
        } finally {
            running.release();
        }
    }

    /**
     * Se descargan todas las ligas antes de escribir: así una liga cuyos partidos no llegaron no
     * escribe nada, y la regla de los dos planteles ve todos los planteles antes de decidir.
     */
    private SyncReport execute(SyncRun run) {
        SyncType type = run.type();
        SyncOrigin origin = run.origin();
        LOGGER.info("Sincronización {} ({}) iniciada.", type, origin);
        List<LeagueSnapshot> snapshots = download(run);
        run.registerSnapshots(snapshots);
        run.resolveDuplicates(playerRepository.findAllByExternalIds(run.duplicatedPlayerExternalIds()));
        snapshots.forEach(snapshot -> write(run, snapshot));
        List<Player> inactivated = run.canDeactivate() ? writeService.deactivateMissing(run) : List.of();
        SyncReport report = run.finish(clock.instant(), inactivated);
        reportLogger.log(report);
        return report;
    }

    /**
     * La excepción se registra con su causa, que es la del cliente HTTP: el informe lleva solo el
     * motivo (research D5).
     */
    private List<LeagueSnapshot> download(SyncRun run) {
        List<LeagueSnapshot> snapshots = new ArrayList<>();
        for (League league : run.leagues()) {
            try {
                snapshots.add(adapter.fetchLeague(league));
            } catch (ExternalSourceException e) {
                LOGGER.warn("{}: no se pudo traer de la fuente.", league, e);
                run.recordFailure(league, e.getMessage());
            }
        }
        return snapshots;
    }

    private void write(SyncRun run, LeagueSnapshot snapshot) {
        League league = snapshot.league();
        try {
            run.recordSuccess(writeService.applyLeague(snapshot, run.squadAssignment()));
        } catch (ExternalSourceException e) {
            LOGGER.warn("{}: no se pudo escribir.", league, e);
            run.recordFailure(league, e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.warn("{}: {}", league, WRITE_FAILURE_REASON, e);
            run.recordFailure(league, WRITE_FAILURE_REASON);
        }
    }
}
