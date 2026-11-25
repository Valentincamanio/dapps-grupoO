package ar.edu.unq.desapp.futbolmarket.config;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.caffeine.CaffeineCache;

import com.github.benmanes.caffeine.cache.Cache;

/**
 * Un {@link CaffeineCache} que nunca guarda el resultado de una consulta en vuelo después de un
 * vaciado (research R3 y data-model §1.3 de la 013; FR-005 y SC-003).
 *
 * <p><b>El problema</b>: con {@code @Cacheable} sin {@code sync}, Spring hace {@code get(key)}; si
 * no encuentra la entrada ejecuta el método y después hace {@code put(key, value)}, todo en el
 * mismo hilo. Si el vaciado de {@code QuoteCacheInvalidator} ocurre entre la lectura de la base y el
 * {@code put}, la consulta en vuelo dejaría guardado el resultado viejo y toda consulta posterior lo
 * recibiría.</p>
 *
 * <p><b>La solución</b> es un número de generación:</p>
 * <ul>
 *     <li>{@link #clear()} e {@link #invalidate()} toman el lock de escritura, suben la generación y
 *     recién después vacían.</li>
 *     <li>Una búsqueda sin entrada anota en el hilo la generación vigente; una con entrada borra la
 *     anotación.</li>
 *     <li>{@link #put} lee y borra la anotación, y bajo el lock de lectura guarda solo si no había
 *     anotación o si la generación no cambió. Si cambió, descarta el valor: esa consulta recibe su
 *     resultado, pero no queda guardado. Los locks hacen atómico el par comprobación-{@code put}
 *     respecto del par incremento-vaciado, y los {@code put} no se bloquean entre sí.</li>
 * </ul>
 *
 * <p>Así, toda consulta que empieza después de confirmado un cambio (el vaciado ocurre después del
 * commit) no encuentra ninguna entrada vieja.</p>
 *
 * <p><b>Límites aceptados</b>:</p>
 * <ul>
 *     <li>La anotación es una por hilo, la de la última búsqueda. Alcanza porque {@code getRanking} y
 *     {@code getCurrent} no anidan otra consulta cacheada entre su búsqueda y su {@code put}.</li>
 *     <li>Si el método lanza (el 404 de {@code getCurrent}), no hay {@code put} y la anotación queda
 *     hasta la próxima búsqueda de ese hilo, que la pisa. Un {@code put} directo de ese hilo después
 *     de un vaciado se descartaría: nunca guarda un dato viejo, a lo sumo pierde un llenado.</li>
 *     <li>{@code @Cacheable(sync = true)} pasa por {@code get(key, Callable)}, que no se protege; el
 *     proyecto no lo usa. {@code retrieve(key)} solo existe para los cachés asíncronos, que tampoco
 *     se usan. {@code putIfAbsent} no lo usa {@code @Cacheable} y queda sin proteger.</li>
 *     <li>Consultas simultáneas de la misma hoja sin guardar pueden llegar las dos a la base.</li>
 * </ul>
 *
 * <p>Es una subclase y no un decorador para que {@code CaffeineCacheMeterBinderProvider} lo
 * reconozca como {@link CaffeineCache} y publique sus métricas (research R3 y R4).</p>
 */
public class GenerationGuardedCaffeineCache extends CaffeineCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationGuardedCaffeineCache.class);

    private final AtomicLong generation = new AtomicLong();
    private final ThreadLocal<Long> lookupGeneration = new ThreadLocal<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public GenerationGuardedCaffeineCache(String name, Cache<Object, Object> cache, boolean allowNullValues) {
        super(name, cache, allowNullValues);
    }

    /**
     * La búsqueda que usan {@code get(key)} y {@code get(key, Class)}, y por eso
     * {@code @Cacheable}. La generación se lee antes de buscar: si un vaciado ocurre en el medio, el
     * {@code put} siguiente se descarta.
     */
    @Override
    protected Object lookup(Object key) {
        long current = generation.get();
        Object value = super.lookup(key);
        if (value == null) {
            lookupGeneration.set(current);
        } else {
            lookupGeneration.remove();
        }
        return value;
    }

    @Override
    public void put(Object key, Object value) {
        Long observed = lookupGeneration.get();
        lookupGeneration.remove();
        lock.readLock().lock();
        try {
            if (observed == null || observed == generation.get()) {
                super.put(key, value);
            } else {
                LOGGER.debug("Se descartó un valor del caché {}: se buscó antes del último vaciado.", getName());
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            generation.incrementAndGet();
            super.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean invalidate() {
        lock.writeLock().lock();
        try {
            generation.incrementAndGet();
            return super.invalidate();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
