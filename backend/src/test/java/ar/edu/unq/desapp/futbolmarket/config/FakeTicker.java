package ar.edu.unq.desapp.futbolmarket.config;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Un reloj de Caffeine que avanza solo cuando el test lo pide (research R6 de la 013), sin la
 * dependencia {@code guava-testlib}.
 */
final class FakeTicker implements Ticker {
    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read() {
        return nanos.get();
    }

    void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }
}
