package ar.edu.unq.desapp.futbolmarket.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

/**
 * Casos borde: cada regla, sin cambios, se evalúa contra las clases de {@code ar.edu.unq.desapp.archfixture}, que la
 * rompen a propósito, y tiene que nombrar al infractor.
 */
class ArchitectureRulesDetectViolationsTest {

    private static final JavaClasses FIXTURES = new ClassFileImporter()
            .importPackages(ArchitectureRules.FIXTURE_BASE);

    @Test
    void laReglaDeCapasDetectaUnControllerQueUsaUnRepository() {
        ArchRule rule = ArchitectureRules.layers(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("RepositoryAwareController"));
    }

    @Test
    void laReglaDeModeloPuroDetectaUnaAnotacionDeJpa() {
        ArchRule rule = ArchitectureRules.pureModel(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("TableMappedItem"));
    }

    @Test
    void laReglaDeJpaConfinadoDetectaJpaEnElModeloYEnUnRepository() {
        ArchRule rule = ArchitectureRules.jpaOnlyInPersistenceSql(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("TableMappedItem"))
                .anyMatch(detail -> detail.contains("LockingRepository"));
    }

    @Test
    void laReglaDeEntidadesDetectaUnaEntidadSinSufijoSQL() {
        ArchRule rule = ArchitectureRules.entities(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("SampleRecord"));
    }

    @Test
    void laReglaDeSQLDAODetectaUnJpaRepositorySinSufijoSQLDAO() {
        ArchRule rule = ArchitectureRules.sqlDaos(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("SampleDao"));
    }

    @Test
    void laReglaDeSQLDAOYMappersDetectaUnServicioQueUsaUnSQLDAO() {
        ArchRule rule = ArchitectureRules.sqlDaoAndMapperAccess(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("DirectDaoService"));
    }

    @Test
    void laReglaDeSQLDAOYMappersDetectaUnServicioQueUsaUnMapper() {
        ArchRule rule = ArchitectureRules.sqlDaoAndMapperAccess(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("MapperAwareService"));
    }

    @Test
    void laReglaDeQueriesNativasDetectaUnaQueryConNativeQueryEnTrue() {
        ArchRule rule = ArchitectureRules.noNativeQueries(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("findAllNative()"));
    }

    @Test
    void laReglaDeQueriesNativasDetectaLaAnotacionNativeQuery() {
        ArchRule rule = ArchitectureRules.noNativeQueries(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("findAllWithNativeQuery()"));
    }

    @Test
    void laReglaDeQueriesNativasDetectaUnaQueryNativaArmadaEnTiempoDeEjecucion() {
        ArchRule rule = ArchitectureRules.noNativeQueries(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("NativeQueryRunner") && detail.contains("createNativeQuery"));
    }

    @Test
    void laReglaDeServicioSinEntidadesDetectaUnServicioQueRecibeUnaEntidad() {
        ArchRule rule = ArchitectureRules.serviceWithoutEntities(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("EntityAwareService"))
                .noneMatch(detail -> detail.contains("SampleService"));
    }

    @Test
    void laReglaDeControllersDetectaUnRestControllerSinSufijoController() {
        ArchRule rule = ArchitectureRules.restControllers(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("SampleEndpoint"));
    }

    @Test
    void laReglaDeDtoDelControllerDetectaUnDtoSinSufijoRequestNiResponse() {
        ArchRule rule = ArchitectureRules.controllerDtos(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("SampleDto"))
                .noneMatch(detail -> detail.contains("SampleResponse$Line"));
    }

    @Test
    void laReglaDeDtoDelControllerDetectaUnServicioQueUsaUnResponse() {
        ArchRule rule = ArchitectureRules.controllerDtos(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("DtoAwareService"));
    }

    @Test
    void laReglaDeEndpointsDetectaModeloDirectoEnUnaListaYEnUnArreglo() {
        ArchRule rule = ArchitectureRules.endpointsReturnNoModel(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("ModelLeakController.item()"))
                .anyMatch(detail -> detail.contains("ModelLeakController.items()"))
                .anyMatch(detail -> detail.contains("ModelLeakController.itemArray()"))
                .anyMatch(detail -> detail.contains("ModelLeakController.entity()"));
    }

    @Test
    void laReglaDeDtoDelProveedorDetectaUnServicioQueUsaUnDtoDelProveedor() {
        ArchRule rule = ArchitectureRules.providerDtos(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("ProviderDtoAwareService"));
    }

    @Test
    void laReglaDeAdapterIndependienteDetectaUnAdapterQueUsaUnServicio() {
        ArchRule rule = ArchitectureRules.independentAdapter(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("ServiceAwareAdapter"));
    }

    @Test
    void laReglaDeClientesHttpDetectaUnServicioQueUsaUnClienteHttp() {
        ArchRule rule = ArchitectureRules.httpClientsOnlyInAdapterAndConfig(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("HttpCallingService"));
    }

    @Test
    void laReglaDeCiclosDetectaUnCicloEntreDosContextosDelServicio() {
        ArchRule rule = ArchitectureRules.serviceContextsWithoutCycles(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("Slice alpha") && detail.contains("Slice beta")
                        && detail.contains("AlphaService") && detail.contains("BetaService"));
    }

    @Test
    void laReglaDeSalidaEstandarDetectaSystemOut() {
        ArchRule rule = ArchitectureRules.noStandardStreams();

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("StandardStreamService"));
    }

    @Test
    void laReglaDeExcepcionesGenericasDetectaUnaRuntimeException() {
        ArchRule rule = ArchitectureRules.noGenericExceptions();

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("GenericExceptionService")
                        && detail.contains("java.lang.RuntimeException"));
    }

    @Test
    void laReglaDeExcepcionesGenericasDetectaUnError() {
        ArchRule rule = ArchitectureRules.noGenericExceptions();

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("GenericExceptionService") && detail.contains("java.lang.Error"));
    }

    @Test
    void laReglaDeJavaUtilLoggingDetectaUnLoggerDeJavaUtilLogging() {
        ArchRule rule = ArchitectureRules.noJavaUtilLogging();

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("JavaUtilLoggingService"));
    }

    @Test
    void laReglaDeInyeccionPorCampoDetectaUnCampoConAutowired() {
        ArchRule rule = ArchitectureRules.noFieldInjection();

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("FieldInjectedService"));
    }

    @Test
    void laReglaDeUnSoloManejadorDetectaUnSegundoManejadorFueraDeShared() {
        ArchRule rule = ArchitectureRules.singleExceptionHandler(ArchitectureRules.FIXTURE_BASE);

        EvaluationResult result = rule.evaluate(FIXTURES);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("StrayExceptionHandler"))
                .anyMatch(detail -> detail.contains("Hay 2 manejadores"));
    }
}
