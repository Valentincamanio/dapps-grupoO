package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.BoostSQL;

public interface BoostSQLDAO extends JpaRepository<BoostSQL, Long> {
    /**
     * Los potenciados de una semana, en el orden en que se eligieron.
     */
    List<BoostSQL> findAllByWeekStartOrderByPositionInWeek(LocalDate weekStart);

    /**
     * Todos los potenciados, por semana y en el orden en que se eligieron: los usa la simulación.
     */
    List<BoostSQL> findAllByOrderByWeekStartAscPositionInWeekAsc();
}
