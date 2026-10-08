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
 * <p>También habilita {@code @Scheduled}, para la sincronización semanal con Football-Data.org,
 * y {@code @Async}, para que la sincronización al arrancar corra en segundo plano y no demore el
 * arranque (research D12 y D13).</p>
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, JwtProperties.class, FootballDataProperties.class})
@EnableScheduling
@EnableAsync
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
