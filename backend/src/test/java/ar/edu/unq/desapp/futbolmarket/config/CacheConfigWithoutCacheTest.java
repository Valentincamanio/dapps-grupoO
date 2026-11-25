package ar.edu.unq.desapp.futbolmarket.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.test.context.ActiveProfiles;

import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteCacheInvalidator;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteQueryService;

/**
 * La variante "sin caché" de la medición del ranking (research R7 de la 013): con
 * {@code spring.cache.type=none} no se crea {@link QuoteCacheManager}, Boot pone un
 * {@link NoOpCacheManager}, cada consulta va a la base y la invalidación de siempre no falla.
 */
@SpringBootTest(properties = {"spring.cache.type=none",
        "spring.datasource.url=jdbc:h2:mem:cache-config-without-cache;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class CacheConfigWithoutCacheTest {
    private static final PlayerFilter NO_FILTER = new PlayerFilter(null, null, null);

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private QuoteCacheInvalidator invalidator;

    @Autowired
    private QuoteQueryService queryService;

    @Test
    void conElCacheApagadoElManagerEsElQueNoGuardaNada() {
        assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);
    }

    @Test
    void dosConsultasIgualesDelRankingNoDevuelvenElMismoObjetoYLaInvalidacionNoFalla() {
        Object first = queryService.getRanking(NO_FILTER, 0, 10);
        Object second = queryService.getRanking(NO_FILTER, 0, 10);

        assertThat(second).isNotSameAs(first).isEqualTo(first);
        assertThatCode(invalidator::invalidateAll).doesNotThrowAnyException();
    }
}
