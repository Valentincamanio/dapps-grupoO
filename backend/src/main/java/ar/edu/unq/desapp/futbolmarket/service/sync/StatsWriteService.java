package ar.edu.unq.desapp.futbolmarket.service.sync;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.MatchStatsLoad;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.performance.PlayerMatchStatsRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import lombok.RequiredArgsConstructor;

/**
 * Las escrituras de la sincronización de WhoScored, cada una en su propia transacción (research
 * D18). Vive en un bean aparte de {@link StatsSyncService} para que {@code @Transactional} se aplique
 * sin auto-invocación.
 *
 * <p>Un partido se guarda todo o nada (FR-013). Los cruces de equipos y de partidos de una liga se
 * guardan apenas se deciden: si la corrida se corta, la siguiente no vuelve a pedir esas páginas.</p>
 */
@Service
@RequiredArgsConstructor
public class StatsWriteService {
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final PlayerMatchStatsRepository statsRepository;

    @Transactional
    public List<Team> saveTeamCrosses(List<Team> teams) {
        return teamRepository.saveAll(teams);
    }

    @Transactional
    public List<Match> saveMatchCrosses(List<Match> matches) {
        return matchRepository.saveAll(matches);
    }

    /**
     * Los ids de WhoScored de los jugadores cruzados en el partido, sus estadísticas y la marca del
     * partido. Si algo falla, no queda nada.
     */
    @Transactional
    public void saveMatchStats(MatchStatsLoad load) {
        playerRepository.saveAll(load.crossedPlayers());
        matchRepository.saveAll(List.of(load.match()));
        statsRepository.saveAll(load.stats());
    }
}
