package ar.edu.unq.desapp.futbolmarket.architecture;

import java.util.ArrayList;
import java.util.List;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

/**
 * Arma el mensaje de falla de una regla: su descripción ({@code [id] nombre, porque porqué}), el encabezado
 * {@code Infractores (n):} y una línea por cada violación.
 */
final class ViolationReport {

    private ViolationReport() {
    }

    static String of(ArchRule rule, EvaluationResult result) {
        List<String> details = result.getFailureReport().getDetails();
        List<String> lines = new ArrayList<>();
        lines.add(rule.getDescription());
        lines.add("Infractores (" + details.size() + "):");
        details.forEach(detail -> lines.add("- " + detail));
        return String.join(System.lineSeparator(), lines);
    }
}
