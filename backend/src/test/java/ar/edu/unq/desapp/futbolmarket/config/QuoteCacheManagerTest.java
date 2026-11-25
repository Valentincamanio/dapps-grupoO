package ar.edu.unq.desapp.futbolmarket.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import com.github.benmanes.caffeine.cache.stats.CacheStats;

/**
 * El manager de los cachés de cotizaciones sin Spring (research R1 y R6 de la 013): nombres fijos,
 * tope y vencimiento con valores propios por caché, y estadísticas prendidas. El executor corre el
 * mantenimiento de Caffeine en el mismo hilo y el reloj avanza a pedido.
 */
class QuoteCacheManagerTest {
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final String KEY = "clave";
    private static final String VALUE = "valor";

    private final FakeTicker ticker = new FakeTicker();

    @Test
    void esUnCaffeineCacheManagerConLosDosCachesDeCotizacionesYNingunoMas() {
        QuoteCacheManager manager = manager(500, TEN_MINUTES, 5000, TEN_MINUTES);

        assertThat(manager).isInstanceOf(CaffeineCacheManager.class);
        assertThat(manager.getCacheNames())
                .containsExactlyInAnyOrder(CacheConfig.QUOTES_RANKING, CacheConfig.QUOTES_CURRENT);
        assertThat(manager.getCache("otro")).isNull();
        assertThat(manager.getCache(CacheConfig.QUOTES_RANKING)).isInstanceOf(GenerationGuardedCaffeineCache.class);
        assertThat(manager.getCache(CacheConfig.QUOTES_CURRENT)).isInstanceOf(GenerationGuardedCaffeineCache.class);
    }

    @Test
    void cadaCacheRespetaSuPropioTopeDespuesDelMantenimiento() {
        QuoteCacheManager manager = manager(3, TEN_MINUTES, 5, TEN_MINUTES);
        Cache ranking = manager.getCache(CacheConfig.QUOTES_RANKING);
        Cache current = manager.getCache(CacheConfig.QUOTES_CURRENT);

        IntStream.range(0, 10).forEach(index -> {
            ranking.put(KEY + index, VALUE);
            current.put(KEY + index, VALUE);
        });

        assertThat(estimatedSize(ranking)).isLessThanOrEqualTo(3);
        assertThat(estimatedSize(current)).isLessThanOrEqualTo(5).isGreaterThan(3);
    }

    @Test
    void unaEntradaDelRankingSeSirveHastaSuVencimientoYDespuesNo() {
        QuoteCacheManager manager = manager(500, TEN_MINUTES, 5000, Duration.ofMinutes(20));
        Cache ranking = manager.getCache(CacheConfig.QUOTES_RANKING);
        ranking.put(KEY, VALUE);

        ticker.advance(Duration.ofMinutes(9).plusSeconds(59));
        Cache.ValueWrapper beforeExpiry = ranking.get(KEY);
        ticker.advance(Duration.ofSeconds(2));
        Cache.ValueWrapper afterExpiry = ranking.get(KEY);

        assertThat(beforeExpiry).isNotNull();
        assertThat(beforeExpiry.get()).isEqualTo(VALUE);
        assertThat(afterExpiry).isNull();
    }

    @Test
    void cadaCacheVenceConSuPropioPlazo() {
        QuoteCacheManager manager = manager(500, TEN_MINUTES, 5000, Duration.ofMinutes(20));
        Cache ranking = manager.getCache(CacheConfig.QUOTES_RANKING);
        Cache current = manager.getCache(CacheConfig.QUOTES_CURRENT);
        ranking.put(KEY, VALUE);
        current.put(KEY, VALUE);

        ticker.advance(Duration.ofMinutes(10).plusSeconds(1));
        Cache.ValueWrapper rankingAfterTen = ranking.get(KEY);
        Cache.ValueWrapper currentAfterTen = current.get(KEY);
        ticker.advance(Duration.ofMinutes(10));
        Cache.ValueWrapper currentAfterTwenty = current.get(KEY);

        assertThat(rankingAfterTen).isNull();
        assertThat(currentAfterTen).isNotNull();
        assertThat(currentAfterTwenty).isNull();
    }

    @Test
    void lasEstadisticasCuentanUnFalloYUnAcierto() {
        QuoteCacheManager manager = manager(500, TEN_MINUTES, 5000, TEN_MINUTES);
        Cache ranking = manager.getCache(CacheConfig.QUOTES_RANKING);

        ranking.get(KEY);
        ranking.put(KEY, VALUE);
        ranking.get(KEY);

        CacheStats stats = nativeCache(ranking).stats();
        assertThat(stats.missCount()).isEqualTo(1);
        assertThat(stats.hitCount()).isEqualTo(1);
    }

    private QuoteCacheManager manager(long rankingSize, Duration rankingExpiry, long currentSize,
            Duration currentExpiry) {
        QuoteCacheProperties properties = new QuoteCacheProperties(
                new QuoteCacheProperties.Spec(rankingSize, rankingExpiry),
                new QuoteCacheProperties.Spec(currentSize, currentExpiry));
        return new QuoteCacheManager(properties, ticker, Runnable::run);
    }

    @SuppressWarnings("unchecked")
    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache(Cache cache) {
        return (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
    }

    private static long estimatedSize(Cache cache) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = nativeCache(cache);
        nativeCache.cleanUp();
        return nativeCache.estimatedSize();
    }
}
