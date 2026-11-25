package ar.edu.unq.desapp.futbolmarket.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Caché en memoria de la cotización vigente de un jugador y del ranking (research D21 de la 007 y
 * R1 de la 013), sobre Caffeine con {@link QuoteCacheManager}.
 *
 * <p>Cada caché tiene un tope de entradas y un vencimiento de seguridad propios
 * ({@code futbolmarket.cache.*}), y estadísticas que salen en {@code /actuator/prometheus}. Se
 * siguen vaciando enteros después de cada recálculo, recotización o cambio de la estrategia activa
 * ({@code QuoteCacheInvalidator}); el vencimiento es solo una red por si falla una invalidación. Los
 * nombres son fijos, así que pedir un caché que no es uno de estos dos devuelve {@code null}.</p>
 *
 * <p>El bean se llama {@code cacheManager}, que es la etiqueta {@code cache_manager} de las
 * métricas. Con {@code spring.cache.type=none} no se crea y Boot pone un {@code NoOpCacheManager}:
 * sirve solo para la variante "sin caché" de la medición (research R7).</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {
    public static final String QUOTES_CURRENT = "quotes-current";
    public static final String QUOTES_RANKING = "quotes-ranking";

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager cacheManager(QuoteCacheProperties properties) {
        return new QuoteCacheManager(properties, Ticker.systemTicker(), null);
    }
}
