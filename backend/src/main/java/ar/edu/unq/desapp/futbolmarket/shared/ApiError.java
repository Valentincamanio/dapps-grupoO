package ar.edu.unq.desapp.futbolmarket.shared;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<Violation> violations
) {
    public record Violation(String field, String message, String rejectedValue) {
    }
}
