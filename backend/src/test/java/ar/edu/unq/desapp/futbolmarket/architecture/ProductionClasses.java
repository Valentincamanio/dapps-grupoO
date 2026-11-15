package ar.edu.unq.desapp.futbolmarket.architecture;

import java.lang.ref.SoftReference;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Clases de producción de la aplicación, importadas una sola vez por corrida y sin las clases de test.
 * {@link JavaClasses} es inmutable, así que compartirla entre los tests no comparte estado.
 * <p>
 * El import ocupa unos 50 MB y la corrida comparte el heap con los contextos de Spring que cachean los
 * {@code @SpringBootTest}. Por eso se guarda en una {@link SoftReference}: si la memoria aprieta, la JVM lo libera
 * antes de quedarse sin heap y la próxima llamada lo vuelve a importar.
 */
final class ProductionClasses {

    private static SoftReference<JavaClasses> cache = new SoftReference<>(null);

    private ProductionClasses() {
    }

    static synchronized JavaClasses get() {
        JavaClasses classes = cache.get();
        if (classes == null) {
            classes = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(ArchitectureRules.PRODUCTION_BASE);
            cache = new SoftReference<>(classes);
        }
        return classes;
    }
}
