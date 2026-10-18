package ar.edu.unq.desapp.futbolmarket.persistence.mapper.performance;

import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.match.MatchMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.player.PlayerMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.performance.PlayerMatchStatsSQL;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * Traduce cada métrica a su columna con dos tablas fijas, una de lectores y otra de escritores.
 * Así las 15 columnas se recorren en un único ciclo sobre {@link Metric} y no se repite un bloque
 * por métrica.
 */
@Component
public class PlayerMatchStatsMapper {
    private static final Map<Metric, ToIntFunction<PlayerMatchStatsSQL>> READERS = readers();
    private static final Map<Metric, ObjIntConsumer<PlayerMatchStatsSQL>> WRITERS = writers();

    private final PlayerMapper playerMapper;
    private final MatchMapper matchMapper;

    public PlayerMatchStatsMapper(PlayerMapper playerMapper, MatchMapper matchMapper) {
        this.playerMapper = playerMapper;
        this.matchMapper = matchMapper;
    }

    public PlayerMatchStats toDomain(PlayerMatchStatsSQL stats) {
        Map<Metric, Integer> counts = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            counts.put(metric, READERS.get(metric).applyAsInt(stats));
        }
        return new PlayerMatchStats(
                stats.getId(),
                playerMapper.toDomain(stats.getPlayer()),
                matchMapper.toDomain(stats.getMatch()),
                stats.isStarter(),
                stats.getMinutesPlayed(),
                MetricCounts.of(counts),
                stats.getRating(),
                stats.isManOfTheMatch()
        );
    }

    public PlayerMatchStatsSQL toSQL(PlayerMatchStats stats) {
        PlayerMatchStatsSQL entity = new PlayerMatchStatsSQL(
                stats.id(),
                playerMapper.toSQL(stats.player()),
                matchMapper.toSQL(stats.match()),
                stats.starter(),
                stats.minutesPlayed(),
                stats.rating(),
                stats.manOfTheMatch()
        );
        for (Metric metric : Metric.values()) {
            WRITERS.get(metric).accept(entity, stats.counts().get(metric));
        }
        return entity;
    }

    private static Map<Metric, ToIntFunction<PlayerMatchStatsSQL>> readers() {
        Map<Metric, ToIntFunction<PlayerMatchStatsSQL>> readers = new EnumMap<>(Metric.class);
        readers.put(Metric.GOALS, PlayerMatchStatsSQL::getGoals);
        readers.put(Metric.ASSISTS, PlayerMatchStatsSQL::getAssists);
        readers.put(Metric.SHOTS, PlayerMatchStatsSQL::getShots);
        readers.put(Metric.SHOTS_ON_TARGET, PlayerMatchStatsSQL::getShotsOnTarget);
        readers.put(Metric.KEY_PASSES, PlayerMatchStatsSQL::getKeyPasses);
        readers.put(Metric.PASSES, PlayerMatchStatsSQL::getPasses);
        readers.put(Metric.ACCURATE_PASSES, PlayerMatchStatsSQL::getAccuratePasses);
        readers.put(Metric.SUCCESSFUL_DRIBBLES, PlayerMatchStatsSQL::getSuccessfulDribbles);
        readers.put(Metric.TACKLES, PlayerMatchStatsSQL::getTackles);
        readers.put(Metric.INTERCEPTIONS, PlayerMatchStatsSQL::getInterceptions);
        readers.put(Metric.CLEARANCES, PlayerMatchStatsSQL::getClearances);
        readers.put(Metric.AERIALS_WON, PlayerMatchStatsSQL::getAerialsWon);
        readers.put(Metric.FOULS_COMMITTED, PlayerMatchStatsSQL::getFoulsCommitted);
        readers.put(Metric.YELLOW_CARDS, PlayerMatchStatsSQL::getYellowCards);
        readers.put(Metric.RED_CARDS, PlayerMatchStatsSQL::getRedCards);
        return Collections.unmodifiableMap(readers);
    }

    private static Map<Metric, ObjIntConsumer<PlayerMatchStatsSQL>> writers() {
        Map<Metric, ObjIntConsumer<PlayerMatchStatsSQL>> writers = new EnumMap<>(Metric.class);
        writers.put(Metric.GOALS, PlayerMatchStatsSQL::setGoals);
        writers.put(Metric.ASSISTS, PlayerMatchStatsSQL::setAssists);
        writers.put(Metric.SHOTS, PlayerMatchStatsSQL::setShots);
        writers.put(Metric.SHOTS_ON_TARGET, PlayerMatchStatsSQL::setShotsOnTarget);
        writers.put(Metric.KEY_PASSES, PlayerMatchStatsSQL::setKeyPasses);
        writers.put(Metric.PASSES, PlayerMatchStatsSQL::setPasses);
        writers.put(Metric.ACCURATE_PASSES, PlayerMatchStatsSQL::setAccuratePasses);
        writers.put(Metric.SUCCESSFUL_DRIBBLES, PlayerMatchStatsSQL::setSuccessfulDribbles);
        writers.put(Metric.TACKLES, PlayerMatchStatsSQL::setTackles);
        writers.put(Metric.INTERCEPTIONS, PlayerMatchStatsSQL::setInterceptions);
        writers.put(Metric.CLEARANCES, PlayerMatchStatsSQL::setClearances);
        writers.put(Metric.AERIALS_WON, PlayerMatchStatsSQL::setAerialsWon);
        writers.put(Metric.FOULS_COMMITTED, PlayerMatchStatsSQL::setFoulsCommitted);
        writers.put(Metric.YELLOW_CARDS, PlayerMatchStatsSQL::setYellowCards);
        writers.put(Metric.RED_CARDS, PlayerMatchStatsSQL::setRedCards);
        return Collections.unmodifiableMap(writers);
    }
}
