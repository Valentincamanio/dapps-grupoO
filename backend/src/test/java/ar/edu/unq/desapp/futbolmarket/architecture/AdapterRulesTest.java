package ar.edu.unq.desapp.futbolmarket.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

class AdapterRulesTest {

    @Test
    void losDtoDelProveedorSoloSeUsanDesdeElAdapter() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.providerDtos(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void elAdapterNoConoceElServicioNiElControllerNiLaPersistencia() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.independentAdapter(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void soloElAdapterYConfigUsanClientesHttp() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.httpClientsOnlyInAdapterAndConfig(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }
}
