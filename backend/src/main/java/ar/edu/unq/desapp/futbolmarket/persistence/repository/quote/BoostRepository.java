package ar.edu.unq.desapp.futbolmarket.persistence.repository.quote;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekBoosts;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.quote.BoostMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.BoostSQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote.BoostSQLDAO;
import lombok.RequiredArgsConstructor;

/**
 * Lee y reemplaza los potenciados de cada semana. Una semana sin filas no tiene potenciados.
 */
@Repository
@RequiredArgsConstructor
public class BoostRepository {
    private final BoostSQLDAO boostDAO;
    private final PlayerSQLDAO playerDAO;
    private final BoostMapper boostMapper;

    @Transactional(readOnly = true)
    public WeekBoosts findWeek(QuoteWeek week) {
        return boostMapper.toDomain(week, boostDAO.findAllByWeekStartOrderByPositionInWeek(week.monday()));
    }

    /**
     * Los potenciados de todas las semanas que tienen alguno, por semana.
     */
    @Transactional(readOnly = true)
    public Map<QuoteWeek, WeekBoosts> findAllByWeek() {
        Map<QuoteWeek, List<BoostSQL>> rowsByWeek = boostDAO.findAllByOrderByWeekStartAscPositionInWeekAsc().stream()
                .collect(Collectors.groupingBy(row -> new QuoteWeek(row.getWeekStart()), TreeMap::new,
                        Collectors.toList()));
        Map<QuoteWeek, WeekBoosts> boosts = new TreeMap<>();
        rowsByWeek.forEach((week, rows) -> boosts.put(week, boostMapper.toDomain(week, rows)));
        return boosts;
    }

    /**
     * Reemplaza los potenciados de la semana. El borrado va en lote y se ejecuta antes de insertar,
     * así las filas nuevas no chocan con el índice único de las viejas. El jugador se referencia sin
     * cargarlo.
     */
    @Transactional
    public WeekBoosts replace(WeekBoosts boosts) {
        QuoteWeek week = boosts.week();
        boostDAO.deleteAllInBatch(boostDAO.findAllByWeekStartOrderByPositionInWeek(week.monday()));
        boostDAO.saveAll(IntStream.range(0, boosts.boosts().size())
                .mapToObj(i -> boostMapper.toSQL(week, boosts.boosts().get(i),
                        playerDAO.getReferenceById(boosts.boosts().get(i).playerId()), i))
                .toList());
        return findWeek(week);
    }
}
