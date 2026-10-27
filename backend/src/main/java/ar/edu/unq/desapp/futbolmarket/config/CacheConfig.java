package ar.edu.unq.desapp.futbolmarket.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caché en memoria de la cotización vigente de un jugador y del ranking (research D21). Usa
 * {@link ConcurrentMapCacheManager} de {@code spring-context}, sin dependencias nuevas.
 *
 * <p>Las entradas no vencen: se invalidan enteras después de cada recálculo, recotización o cambio
 * de la estrategia activa ({@code QuoteCacheInvalidator}). Los nombres son fijos, así que pedir un
 * caché que no es uno de estos dos es un error.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {
    public static final String QUOTES_CURRENT = "quotes-current";
    public static final String QUOTES_RANKING = "quotes-ranking";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(QUOTES_CURRENT, QUOTES_RANKING);
    }
}
