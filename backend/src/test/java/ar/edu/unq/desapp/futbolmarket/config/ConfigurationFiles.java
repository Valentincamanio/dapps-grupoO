package ar.edu.unq.desapp.futbolmarket.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Lee los archivos de configuración del classpath sin levantar Spring, como
 * {@code FootballDataPropertiesTest}. Lo comparten los tests de las propiedades y del cliente de
 * WhoScored, que leen los mismos archivos.
 */
final class ConfigurationFiles {

    static final String APPLICATION_YAML = "application.yaml";
    static final String APPLICATION_TEST_YML = "application-test.yml";

    private ConfigurationFiles() {
    }

    /**
     * Las fuentes de los archivos, en el orden recibido. Un valor del primero tapa al de los
     * siguientes, como un perfil sobre la configuración base.
     */
    static List<PropertySource<?>> load(String... names) throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = new ArrayList<>();
        for (String name : names) {
            sources.addAll(loader.load(name, new ClassPathResource(name)));
        }
        return sources;
    }

    /**
     * Enlaza los archivos con las mismas reglas de conversión que el enlace de propiedades (por
     * ejemplo, {@code 10s} a {@code Duration}).
     */
    static Binder binder(String... names) throws IOException {
        return new Binder(ConfigurationPropertySources.from(load(names)));
    }
}
