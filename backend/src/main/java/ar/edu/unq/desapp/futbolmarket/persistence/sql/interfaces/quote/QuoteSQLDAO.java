package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.quote.QuoteSQL;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Las consultas de cotizaciones son JPQL o derivadas, nunca SQL nativo (Principio VI). Cada
 * historia suma las suyas.
 */
public interface QuoteSQLDAO extends JpaRepository<QuoteSQL, Long> {
}
