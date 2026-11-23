package ar.edu.unq.desapp.futbolmarket.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

class ControllerRulesTest {

    @Test
    void losControllersRestVivenEnSuContextoYTerminanEnController() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.restControllers(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void losDtoDelControllerSoloSeUsanDesdeElControllerYSeLlamanRequestOResponse() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.controllerDtos(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void ningunEndpointDevuelveClasesDeModeloNiDePersistencia() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.endpointsReturnNoModel(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }
}
