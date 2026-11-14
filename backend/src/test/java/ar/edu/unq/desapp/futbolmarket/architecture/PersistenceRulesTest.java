package ar.edu.unq.desapp.futbolmarket.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

class PersistenceRulesTest {

    @Test
    void jpaSoloSeUsaDentroDePersistenceSql() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.jpaOnlyInPersistenceSql(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void lasEntidadesVivenEnPersistenceSqlEntityYTerminanEnSQL() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.entities(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void losSQLDAOSonInterfacesDePersistenceSqlInterfacesQueExtiendenJpaRepository() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.sqlDaos(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void losSQLDAOYLosMappersSoloSeUsanDesdeRepository() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.sqlDaoAndMapperAccess(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void ningunaQueryEsNativa() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.noNativeQueries(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }

    @Test
    void elServicioNoConoceLasEntidades() {
        JavaClasses classes = ProductionClasses.get();
        ArchRule rule = ArchitectureRules.serviceWithoutEntities(ArchitectureRules.PRODUCTION_BASE);

        EvaluationResult result = rule.evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .withFailMessage(() -> ViolationReport.of(rule, result))
                .isEmpty();
    }
}
