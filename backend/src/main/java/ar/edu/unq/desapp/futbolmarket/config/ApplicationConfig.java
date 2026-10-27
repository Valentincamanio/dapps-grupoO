package ar.edu.unq.desapp.futbolmarket.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las propiedades de la aplicación y expone el reloj como bean, para que los componentes
 * que dependen del tiempo se puedan testear con un reloj fijo.
 *
 * <p>También habilita {@code @Scheduled}, para la sincronización semanal con Football-Data.org y
 * el recálculo semanal de las cotizaciones, y {@code @Async}, para que la sincronización y el
 * recálculo al arrancar corran en segundo plano y no demoren el arranque (research D12 y D13; D17
 * de la 007).</p>
 *
 * <p>Las propiedades de WhoScored y el plazo de la puesta al día al arrancar se validan al
 * enlazarse: con un valor por debajo de los mínimos del spec, la aplicación no arranca (research
 * D25 de la 006).</p>
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, JwtProperties.class, FootballDataProperties.class,
        WhoScoredProperties.class, SyncProperties.class, QuoteRecalculationProperties.class})
@EnableScheduling
@EnableAsync
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
