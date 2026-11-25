package ar.edu.unq.desapp.futbolmarket.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * La carrera de research R3 de la 013 armada a mano: una búsqueda sin entrada, un vaciado en el
 * medio y el {@code put} de esa consulta, que se descarta. Lo que no tiene vaciado en el medio se
 * guarda como siempre.
 */
class GenerationGuardedCaffeineCacheTest {
    private static final String KEY = "clave";
    private static final String OTHER_KEY = "otra";
    private static final String VALUE = "viejo";
    private static final String OTHER_VALUE = "otro";
    private static final long TIMEOUT_SECONDS = 10;

    private GenerationGuardedCaffeineCache cache;
    private ExecutorService executor;

    @BeforeEach
    void prepare() {
        cache = new GenerationGuardedCaffeineCache("prueba",
                Caffeine.newBuilder().executor(Runnable::run).ticker(new FakeTicker()).build(), true);
    }

    @AfterEach
    void stopExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void unPutDespuesDelVaciadoSeDescarta() {
        cache.get(KEY);
        cache.clear();

        cache.put(KEY, VALUE);

        assertThat(cache.get(KEY)).isNull();
    }

    @Test
    void unPutDespuesDeLaInvalidacionSeDescarta() {
        cache.get(KEY);
        cache.invalidate();

        cache.put(KEY, VALUE);

        assertThat(cache.get(KEY)).isNull();
    }

    @Test
    void unPutSinVaciadoEnElMedioSeGuarda() {
        cache.get(KEY);

        cache.put(KEY, VALUE);

        assertThat(valueOf(KEY)).isEqualTo(VALUE);
    }

    @Test
    void unPutSinBusquedaPreviaSeGuarda() {
        cache.put(KEY, VALUE);

        assertThat(valueOf(KEY)).isEqualTo(VALUE);
    }

    @Test
    void unaBusquedaConEntradaBorraLaAnotacion() {
        cache.put(KEY, VALUE);
        cache.get(KEY);
        cache.clear();

        cache.put(OTHER_KEY, OTHER_VALUE);

        assertThat(valueOf(OTHER_KEY)).isEqualTo(OTHER_VALUE);
    }

    @Test
    void unPutConsumeLaAnotacion() {
        cache.get(KEY);
        cache.put(KEY, VALUE);
        cache.clear();

        cache.put(KEY, OTHER_VALUE);

        assertThat(valueOf(KEY)).isEqualTo(OTHER_VALUE);
    }

    @Test
    void sacarUnaEntradaNoSubeLaGeneracion() {
        cache.get(KEY);
        cache.evict(OTHER_KEY);

        cache.put(KEY, VALUE);

        assertThat(valueOf(KEY)).isEqualTo(VALUE);
    }

    @Test
    void laAnotacionDeUnHiloNoAfectaAOtro() throws Exception {
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch looked = new CountDownLatch(1);
        CountDownLatch cleared = new CountDownLatch(1);
        Future<?> stale = executor.submit(() -> {
            cache.get(KEY);
            looked.countDown();
            await(cleared);
            cache.put(KEY, VALUE);
        });
        assertThat(looked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        cache.clear();
        Future<?> fresh = executor.submit(() -> {
            cache.get(OTHER_KEY);
            cache.put(OTHER_KEY, OTHER_VALUE);
        });

        fresh.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        cleared.countDown();
        stale.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(cache.get(KEY)).isNull();
        assertThat(valueOf(OTHER_KEY)).isEqualTo(OTHER_VALUE);
    }

    private Object valueOf(String key) {
        Cache.ValueWrapper wrapper = cache.get(key);
        assertThat(wrapper).isNotNull();
        return wrapper.get();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
