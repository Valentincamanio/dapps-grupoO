package ar.edu.unq.desapp.futbolmarket.service.sync;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.config.FootballDataProperties;
import ar.edu.unq.desapp.futbolmarket.config.SyncProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.CatchUp;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.CatchUpPolicy;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.LastFullSyncRepository;
import lombok.RequiredArgsConstructor;

/**
 * Encadena las dos fuentes: Football-Data y después WhoScored (research D22). La cadena toma el
 * candado común de punta a punta, así cuenta como una sola sincronización y ningún disparo manual
 * se mete entre los dos pasos (FR-041).
 *
 * <p>El paso de Football-Data se saltea si falta su credencial. El de WhoScored corre siempre,
 * aunque Football-Data haya fallado en alguna liga o en todas (FR-036). Un error de programación no
 * se captura y aborta la cadena. Si hay otra sincronización en curso, la
 * {@code SyncInProgressException} se propaga: la manejan {@link StartupSync} y
 * {@link SyncScheduler}.</p>
 */
@Service
@RequiredArgsConstructor
public class SyncCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncCoordinator.class);
    private static final String CHAIN_WITHOUT_ANY = "Puesta al día al arrancar: no hay una sincronización completa "
            + "exitosa de Football-Data.org registrada: se corren las dos sincronizaciones en segundo plano.";
    private static final String CHAIN_AFTER_EXPIRED = "Puesta al día al arrancar: la última sincronización completa "
            + "exitosa de Football-Data.org terminó el {}: se corren las dos sincronizaciones en segundo plano.";
    private static final String WHOSCORED_WITHOUT_ANY = "Puesta al día al arrancar: no hay una sincronización "
            + "completa exitosa de WhoScored registrada: se corre solo la de WhoScored en segundo plano.";
    private static final String WHOSCORED_AFTER_EXPIRED = "Puesta al día al arrancar: la última sincronización "
            + "completa exitosa de WhoScored terminó el {}: se corre solo la de WhoScored en segundo plano.";

    private final FootballDataProperties footballDataProperties;
    private final SyncProperties syncProperties;
    private final SyncLock lock;
    private final SyncService syncService;
    private final StatsSyncService statsSyncService;
    private final LastFullSyncRepository lastFullSyncRepository;
    private final Clock clock;

    /**
     * La cadena del lunes (FR-033).
     */
    public void runWeeklyChain() {
        runChain(SyncOrigin.WEEKLY);
    }

    /**
     * La puesta al día al arrancar, según la última completa exitosa de cada fuente (FR-034 y
     * FR-035).
     */
    public void catchUpOnStartup() {
        Optional<LastFullSync> footballData = lastFullSyncRepository.find(SyncSource.FOOTBALL_DATA);
        Optional<LastFullSync> whoScored = lastFullSyncRepository.find(SyncSource.WHOSCORED);
        CatchUp decision = new CatchUpPolicy(syncProperties.catchUpAfter())
                .decide(footballData, whoScored, clock.instant());
        switch (decision) {
            case CHAIN -> {
                footballData.ifPresentOrElse(
                        last -> LOGGER.info(CHAIN_AFTER_EXPIRED, last.finishedAt()),
                        () -> LOGGER.info(CHAIN_WITHOUT_ANY));
                runChain(SyncOrigin.CATCH_UP);
            }
            case WHOSCORED_ONLY -> {
                whoScored.ifPresentOrElse(
                        last -> LOGGER.info(WHOSCORED_AFTER_EXPIRED, last.finishedAt()),
                        () -> LOGGER.info(WHOSCORED_WITHOUT_ANY));
                statsSyncService.synchronizeAll(SyncOrigin.CATCH_UP);
            }
            case NONE -> LOGGER.info("Las dos fuentes están al día: no se sincroniza al arrancar.");
        }
    }

    private void runChain(SyncOrigin origin) {
        lock.runExclusively(() -> chain(origin));
    }

    private StatsSyncReport chain(SyncOrigin origin) {
        if (footballDataProperties.hasToken()) {
            syncService.runFull(origin);
        } else {
            LOGGER.info("Se omite Football-Data.org en la cadena: falta la credencial (FOOTBALL_DATA_TOKEN).");
        }
        return statsSyncService.runFull(origin);
    }
}
