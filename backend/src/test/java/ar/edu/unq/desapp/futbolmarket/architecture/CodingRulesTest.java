package ar.edu.unq.desapp.futbolmarket.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

class CodingRulesTest {

    @Test
    void ningunaClaseUsaLaSalidaEstandar() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.noStandardStreams();

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void ningunaClaseCreaExcepcionesGenericas() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.noGenericExceptions();

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void ningunaClaseUsaJavaUtilLogging() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.noJavaUtilLogging();

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void ningunaClaseUsaInyeccionPorCampo() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.noFieldInjection();

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void hayUnSoloManejadorDeExcepcionesYViveEnShared() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.singleExceptionHandler(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }
}
