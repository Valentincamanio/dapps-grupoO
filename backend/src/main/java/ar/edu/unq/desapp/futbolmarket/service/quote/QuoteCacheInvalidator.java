package ar.edu.unq.desapp.futbolmarket.service.quote;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.config.CacheConfig;

/**
 * Vacía los dos cachés de cotizaciones (research D21).
 *
 * <p>Se llama siempre <strong>después del commit</strong>, desde los servicios que no son
 * transaccionales: si se vaciara dentro de la transacción, un lector podría volver a llenarlo con
 * los datos viejos antes de que se vean los nuevos.</p>
 */
@Component
public class QuoteCacheInvalidator {

    @CacheEvict(cacheNames = {CacheConfig.QUOTES_CURRENT, CacheConfig.QUOTES_RANKING}, allEntries = true)
    public void invalidateAll() {
        // El vaciado lo hace @CacheEvict al volver del método.
    }
}
