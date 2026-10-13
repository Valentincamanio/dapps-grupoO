package ar.edu.unq.desapp.futbolmarket.adapter.footballdata;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Doble de {@link Sleeper}: registra cada espera y no duerme, así los tests del límite de consultas
 * son instantáneos.
 */
public class RecordingSleeper implements Sleeper {
    private final List<Duration> sleeps = new ArrayList<>();

    @Override
    public void sleep(Duration duration) {
        sleeps.add(duration);
    }

    public List<Duration> sleeps() {
        return List.copyOf(sleeps);
    }
}
