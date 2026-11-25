package ar.edu.unq.desapp.futbolmarket.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El tope de entradas y el vencimiento de seguridad de los dos cachés de cotizaciones (research R5
 * de la 013; FR-001, FR-002, FR-003 y FR-030), cada uno con sus valores.
 *
 * <p>Se validan al enlazar las propiedades: un tope o un vencimiento ausente, cero o negativo
 * impide arrancar, con un mensaje en español que nombra la propiedad completa (por ejemplo,
 * {@code futbolmarket.cache.ranking.maximum-size}). La validación va en el constructor y no en
 * anotaciones de Bean Validation sobre {@link Spec}: {@code Spec} se comparte entre los dos cachés,
 * y el error de enlace de una anotación nombra el campo en camelCase ({@code ranking.maximumSize}),
 * no la propiedad. Los valores por defecto viven en {@code application.yaml} y los perfiles no los
 * pisan.</p>
 */
@ConfigurationProperties("futbolmarket.cache")
public record QuoteCacheProperties(Spec ranking, Spec current) {

    private static final String PREFIX = "futbolmarket.cache.";

    public QuoteCacheProperties {
        requireValid(ranking, "del ranking", "ranking");
        requireValid(current, "de la cotización actual", "current");
    }

    private static void requireValid(Spec spec, String cache, String key) {
        String property = PREFIX + key;
        if (spec == null) {
            throw new IllegalArgumentException("Falta la configuración del caché " + cache + " (" + property + ").");
        }
        if (spec.maximumSize() == null) {
            throw new IllegalArgumentException("Falta el tope del caché " + cache + " (" + property + ".maximum-size).");
        }
        if (spec.maximumSize() <= 0) {
            throw new IllegalArgumentException("El tope del caché " + cache + " (" + property
                    + ".maximum-size) tiene que ser mayor que cero.");
        }
        Duration expiry = spec.expireAfterWrite();
        if (expiry == null) {
            throw new IllegalArgumentException("Falta el vencimiento del caché " + cache + " (" + property
                    + ".expire-after-write).");
        }
        if (expiry.isZero() || expiry.isNegative()) {
            throw new IllegalArgumentException("El vencimiento del caché " + cache + " (" + property
                    + ".expire-after-write) tiene que ser mayor que cero.");
        }
    }

    /**
     * Los valores de un caché: cuántas entradas guarda como mucho y cuánto tiempo, desde que se
     * guardó, se sirve una entrada.
     */
    public record Spec(Long maximumSize, Duration expireAfterWrite) {
    }
}
