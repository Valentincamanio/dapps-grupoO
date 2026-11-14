package ar.edu.unq.desapp.futbolmarket.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Clases de producción de la aplicación, importadas una sola vez por corrida y sin las clases de test.
 * {@link JavaClasses} es inmutable, así que compartirla entre los tests no comparte estado.
 */
final class ProductionClasses {

    private ProductionClasses() {
    }

    static JavaClasses get() {
        return Holder.CLASSES;
    }

    private static final class Holder {

        private static final JavaClasses CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ArchitectureRules.PRODUCTION_BASE);
    }
}
