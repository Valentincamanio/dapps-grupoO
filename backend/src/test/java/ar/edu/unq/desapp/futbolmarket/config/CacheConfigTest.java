package ar.edu.unq.desapp.futbolmarket.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.context.ActiveProfiles;

import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteCacheInvalidator;

/**
 * El contexto levanta con nuestro {@link CacheManager}, con los dos cachés de cotizaciones, y el
 * invalidador los vacía.
 */
@SpringBootTest
@ActiveProfiles("test")
class CacheConfigTest {
    private static final String KEY = "clave";

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private QuoteCacheInvalidator invalidator;

    @Test
    void elCacheManagerEsElNuestroConLosDosCachesDeCotizaciones() {
        assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
        assertThat(cacheManager.getCacheNames())
                .containsExactlyInAnyOrder(CacheConfig.QUOTES_CURRENT, CacheConfig.QUOTES_RANKING);
    }

    @Test
    void unCacheQueNoEsDeCotizacionesNoExiste() {
        assertThat(cacheManager.getCache("otro")).isNull();
    }

    @Test
    void elInvalidadorVaciaLosDosCaches() {
        Cache current = cacheManager.getCache(CacheConfig.QUOTES_CURRENT);
        Cache ranking = cacheManager.getCache(CacheConfig.QUOTES_RANKING);
        current.put(KEY, "actual");
        ranking.put(KEY, "ranking");

        invalidator.invalidateAll();

        assertThat(current.get(KEY)).isNull();
        assertThat(ranking.get(KEY)).isNull();
    }
}
