package ar.edu.unq.desapp.futbolmarket.config;

import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Los dos cachés de cotizaciones sobre Caffeine (research R1, R4 y R6 de la 013), con nombres fijos
 * y valores propios para cada uno (FR-030).
 *
 * <p>Caffeine descarta con su política W-TinyLFU al llegar al tope ({@code maximumSize}, FR-001),
 * no devuelve una entrada pasado su vencimiento de seguridad ({@code expireAfterWrite}, FR-002) y
 * lleva estadísticas ({@code recordStats}), que la autoconfiguración de Boot publica en
 * {@code /actuator/prometheus} como {@code cache_gets_total}, {@code cache_evictions_total} y
 * {@code cache_size}. Cada caché es un {@link GenerationGuardedCaffeineCache}, que nunca guarda el
 * resultado de una consulta en vuelo después de un vaciado (FR-005).</p>
 *
 * <p>El manager es estático: {@code setCacheNames(List.of())} apaga la creación dinámica, así que
 * {@code getCache} con otro nombre devuelve {@code null} y {@code getCacheNames} devuelve solo los
 * dos registrados. Los cachés se registran al construirse porque
 * {@code CacheMetricsRegistrarConfiguration} registra en Micrometer solo los que existen en ese
 * momento (R4). {@code registerCustomCache} pasa por {@link #adaptCaffeineCache}.</p>
 *
 * <p>El {@link Ticker} y el {@link Executor} son para los tests: producción usa el reloj del
 * sistema y el executor por defecto de Caffeine ({@code null}). Caffeine aplica el tope y el
 * vencimiento en su mantenimiento, que es asíncrono, así que puede pasarse momentáneamente del
 * tope (R6). Versión de Caffeine: la del BOM de Boot 4.1.1.</p>
 */
public class QuoteCacheManager extends CaffeineCacheManager {

    public QuoteCacheManager(QuoteCacheProperties properties, Ticker ticker, Executor executor) {
        setCacheNames(List.of());
        registerCustomCache(CacheConfig.QUOTES_RANKING, build(properties.ranking(), ticker, executor).build());
        registerCustomCache(CacheConfig.QUOTES_CURRENT, build(properties.current(), ticker, executor).build());
    }

    @Override
    protected Cache adaptCaffeineCache(String name, com.github.benmanes.caffeine.cache.Cache<Object, Object> cache) {
        return new GenerationGuardedCaffeineCache(name, cache, isAllowNullValues());
    }

    private static Caffeine<Object, Object> build(QuoteCacheProperties.Spec spec, Ticker ticker, Executor executor) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(spec.maximumSize())
                .expireAfterWrite(spec.expireAfterWrite())
                .recordStats()
                .ticker(ticker);
        if (executor != null) {
            builder.executor(executor);
        }
        return builder;
    }
}
