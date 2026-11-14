package ar.edu.unq.desapp.futbolmarket.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import ar.edu.unq.desapp.futbolmarket.FutbolMarketApplication;

class LayerRulesTest {

    @Test
    void lasCapasRespetanLaDireccionControllerServicioPersistenciaYAdapter() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.layers(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void losContextosDelServicioNoFormanCiclos() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.serviceContextsWithoutCycles(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void elImportDeProduccionNoIncluyeTestsNiFixtures() {
        JavaClasses classes = ProductionClasses.get();

        boolean hasApplication = classes.contain(FutbolMarketApplication.class);
        boolean hasTests = classes.contain(LayerRulesTest.class);
        List<String> fixtures = classes.stream()
                .map(JavaClass::getName)
                .filter(name -> name.startsWith(ArchitectureRules.FIXTURE_BASE + "."))
                .toList();

        assertThat(hasApplication).isTrue();
        assertThat(hasTests).isFalse();
        assertThat(fixtures).isEmpty();
    }
}
