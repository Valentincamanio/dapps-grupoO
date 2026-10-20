package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.strategy;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.strategy.StrategySQL;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StrategySQLDAO extends JpaRepository<StrategySQL, Long> {
    /**
     * Todas las versiones con sus pesos, en una sola consulta. Son pocas filas: el catálogo se
     * carga entero (research D13).
     */
    @Override
    @EntityGraph(attributePaths = {"root", "weights"})
    List<StrategySQL> findAll();

    /**
     * La fila marcada como activa; hay a lo sumo una.
     */
    Optional<StrategySQL> findByActiveTrue();
}
