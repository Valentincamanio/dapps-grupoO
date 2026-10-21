package ar.edu.unq.desapp.futbolmarket.persistence.repository.performance;

import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.performance.PlayerMatchStatsMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.performance.PlayerMatchStatsSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.performance.PlayerMatchStatsSQLDAO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Guarda y lee el rendimiento de los jugadores. No borra nada: una estadística guardada nunca
 * cambia ni se pierde (SC-008).
 */
@Repository
public class PlayerMatchStatsRepository {
    private final PlayerMatchStatsSQLDAO statsDAO;
    private final PlayerMatchStatsMapper statsMapper;

    public PlayerMatchStatsRepository(PlayerMatchStatsSQLDAO statsDAO, PlayerMatchStatsMapper statsMapper) {
        this.statsDAO = statsDAO;
        this.statsMapper = statsMapper;
    }

    @Transactional
    public List<PlayerMatchStats> saveAll(List<PlayerMatchStats> stats) {
        List<PlayerMatchStatsSQL> saved = statsDAO.saveAll(stats.stream().map(statsMapper::toSQL).toList());
        return toStats(saved);
    }

    /**
     * Todas las estadísticas, con el jugador y su equipo, y el partido con su temporada y sus
     * equipos, en una sola consulta.
     */
    @Transactional(readOnly = true)
    public List<PlayerMatchStats> findAll() {
        return toStats(statsDAO.findAll());
    }

    private List<PlayerMatchStats> toStats(List<PlayerMatchStatsSQL> stats) {
        return stats.stream().map(statsMapper::toDomain).toList();
    }
}
