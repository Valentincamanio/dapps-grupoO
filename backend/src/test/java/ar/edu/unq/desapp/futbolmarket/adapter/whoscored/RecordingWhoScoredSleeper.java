package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Doble de {@link WhoScoredSleeper}: registra cada espera y no duerme, así los tests del ritmo y del
 * reintento son instantáneos.
 */
public class RecordingWhoScoredSleeper implements WhoScoredSleeper {
    private final List<Duration> sleeps = new ArrayList<>();

    @Override
    public void sleep(Duration duration) {
        sleeps.add(duration);
    }

    public List<Duration> sleeps() {
        return List.copyOf(sleeps);
    }
}
