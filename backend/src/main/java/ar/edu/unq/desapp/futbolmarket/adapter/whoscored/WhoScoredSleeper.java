package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.time.Duration;

/**
 * La espera entre pedidos a WhoScored y antes del reintento. Es inyectable para que los tests
 * registren las esperas sin dormir (research D6).
 */
public interface WhoScoredSleeper {
    void sleep(Duration duration);
}
