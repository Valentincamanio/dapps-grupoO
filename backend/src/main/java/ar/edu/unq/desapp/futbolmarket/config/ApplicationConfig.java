package ar.edu.unq.desapp.futbolmarket.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita las propiedades de la feature y expone el reloj como bean, para que los componentes
 * que dependen del tiempo se puedan testear con un reloj fijo.
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, JwtProperties.class})
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
