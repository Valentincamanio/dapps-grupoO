package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.TradeSQL;

/**
 * Acceso a la tabla {@code market_trades}. Las consultas del historial y del portfolio las suman
 * sus historias; ninguna es SQL nativo (principio VI).
 */
public interface TradeSQLDAO extends JpaRepository<TradeSQL, Long> {
}
