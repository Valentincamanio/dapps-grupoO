package ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.TradeSQL;

/**
 * Acceso a la tabla {@code market_trades}. Las consultas del historial y del portfolio las suman
 * sus historias; ninguna es SQL nativo (principio VI).
 */
public interface TradeSQLDAO extends JpaRepository<TradeSQL, Long> {

    /**
     * La ganancia realizada acumulada en las ventas del usuario. Nula si nunca vendió.
     */
    @Query("select sum(t.realizedProfit) from TradeSQL t where t.seller.id = :userId")
    BigDecimal sumRealizedProfitOfSeller(@Param("userId") Long userId);
}
