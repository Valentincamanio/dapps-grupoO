package ar.edu.unq.desapp.futbolmarket.service.sync;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.LastFullSyncRepository;
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
 * <p>Hay a lo sumo una sincronización a la vez, de cualquiera de las dos fuentes (research D22).
 * Primero se revisa que esté habilitada (503) y después que no haya otra en curso (409). El
 * candado común no espera: un segundo pedido se rechaza en lugar de encolarse.</p>
 *
 * <p>Una completa con las cinco ligas en éxito queda registrada como la última completa exitosa de
 * Football-Data, también si la disparó un administrador (FR-047).</p>
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
    private final LastFullSyncRepository lastFullSyncRepository;
    private final SyncLock syncLock;
    private final Clock clock;

    public SyncReport synchronizeAll(SyncOrigin origin) {
        return synchronize(() -> runFull(origin));
    }

    public SyncReport synchronizeLeague(League league) {
        return synchronize(() -> execute(SyncRun.singleLeague(league, clock.instant())));
    }

    /**
     * Las cinco ligas sin revisar el token ni tomar el candado: la usa la cadena, que ya lo tiene y
     * ya revisó el token. La corrida se crea recién acá, así su inicio es el de la sincronización.
     */
    SyncReport runFull(SyncOrigin origin) {
        return execute(SyncRun.full(origin, clock.instant()));
    }

    private SyncReport synchronize(Supplier<SyncReport> sync) {
        if (!properties.hasToken()) {
            throw new SyncDisabledException();
        }
        return syncLock.runExclusively(sync);
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
        report.lastFullSync().ifPresent(lastFullSyncRepository::save);
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
