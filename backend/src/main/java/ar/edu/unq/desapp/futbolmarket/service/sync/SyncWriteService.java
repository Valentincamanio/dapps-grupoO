package ar.edu.unq.desapp.futbolmarket.service.sync;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueSyncResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SquadAssignment;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncRun;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.season.SeasonRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import lombok.RequiredArgsConstructor;

/**
 * Las escrituras de la sincronización, cada una en su propia transacción (research D8 y D10).
 *
 * <p>Vive en un bean aparte de {@link SyncService} para que {@code @Transactional} se aplique sin
 * auto-invocación. Una liga se escribe toda o nada: si algo falla, se revierte entera y las demás
 * no se enteran (FR-034 y FR-035).</p>
 *
 * <p>Cada paso carga lo guardado por {@code externalId}, le pide al modelo qué guardar y lo guarda:
 * las decisiones son de {@link LeagueSync} y de {@link SyncRun}, no de este servicio.</p>
 */
@Service
@RequiredArgsConstructor
public class SyncWriteService {
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final SeasonRepository seasonRepository;
    private final MatchRepository matchRepository;

    /**
     * Escribe una liga ya descargada. Los equipos que referencian los partidos se cargan después de
     * guardar los de la liga, así un partido entre dos equipos de la liga los encuentra.
     */
    @Transactional
    public LeagueSyncResult applyLeague(LeagueSnapshot snapshot, SquadAssignment assignment) {
        LeagueSync sync = new LeagueSync(snapshot, assignment);
        List<Team> savedTeams = teamRepository.saveAll(
                sync.teamsToSave(teamRepository.findAllByExternalIds(snapshot.teamExternalIds())));
        playerRepository.saveAll(
                sync.playersToSave(playerRepository.findAllByExternalIds(snapshot.playerExternalIds()), savedTeams));
        Season savedSeason = seasonRepository.save(
                sync.seasonToSave(seasonRepository.findByExternalId(snapshot.season().externalId())));
        List<Team> catalogTeams = teamRepository.findAllByExternalIds(snapshot.matchTeamExternalIds());
        matchRepository.saveAll(sync.matchesToSave(
                matchRepository.findAllByExternalIds(snapshot.matchExternalIds()), savedSeason, catalogTeams));
        return sync.result();
    }

    /**
     * Inactiva a los jugadores activos que no aparecieron en ningún plantel, si la corrida lo
     * permite, y devuelve los inactivados. Ninguno se borra: conservan su último equipo (FR-017).
     */
    @Transactional
    public List<Player> deactivateMissing(SyncRun run) {
        List<Player> toDeactivate = run.playersToDeactivate(playerRepository.findAllActive()).stream()
                .map(Player::deactivate)
                .toList();
        return playerRepository.saveAll(toDeactivate);
    }
}
