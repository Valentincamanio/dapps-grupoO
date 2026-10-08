package ar.edu.unq.desapp.futbolmarket.adapter.footballdata;

import java.time.Duration;

/**
 * La espera por el límite de consultas de la fuente. Es inyectable para que los tests registren
 * las esperas sin dormir (research D6).
 */
public interface Sleeper {
    void sleep(Duration duration);
}
