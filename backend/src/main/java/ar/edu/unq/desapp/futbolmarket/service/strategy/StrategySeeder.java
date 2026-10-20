package ar.edu.unq.desapp.futbolmarket.service.strategy;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StartingStrategies;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyCatalog;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.strategy.StrategyRepository;
import lombok.RequiredArgsConstructor;

/**
 * Siembra las estrategias de arranque (research D13), como {@code AdminAccountInitializer}.
 *
 * <p>Corre en todos los perfiles y es idempotente: crea cada estrategia de {@link StartingStrategies}
 * solo si no hay una con ese nombre (sin distinguir mayúsculas ni espacios de los extremos), y no
 * toca las existentes aunque el administrador las haya editado (FR-031, US4.2). La primera que
 * entra en un catálogo vacío queda activa, así que en una base nueva la activa es la A; en una base
 * con estrategias el catálogo ya tiene exactamente una activa y la semilla no la cambia.</p>
 *
 * <p>No es {@code @Transactional}: cada guardado abre la suya, como el alta del administrador.</p>
 */
@Component
@RequiredArgsConstructor
public class StrategySeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(StrategySeeder.class);

    private final StrategyRepository strategyRepository;

    @Override
    public void run(ApplicationArguments args) {
        StrategyCatalog catalog = strategyRepository.findCatalog();
        List<String> created = new ArrayList<>();
        List<String> existing = new ArrayList<>();
        for (StartingStrategies.Definition definition : StartingStrategies.all()) {
            if (catalog.findByName(definition.name()) != null) {
                existing.add(definition.name());
                continue;
            }
            catalog = strategyRepository.save(catalog.create(definition.name(), definition.scaleFactor(),
                    definition.minimumValue(), definition.defaultWeights(), definition.positionWeights()));
            created.add(definition.name());
        }
        log(created, existing, catalog);
    }

    private static void log(List<String> created, List<String> existing, StrategyCatalog catalog) {
        if (!created.isEmpty()) {
            LOGGER.info("Se crearon las estrategias de arranque: {}.", String.join(", ", created));
        }
        if (!existing.isEmpty()) {
            LOGGER.info("Las estrategias de arranque ya existían y no se modificaron: {}.", String.join(", ", existing));
        }
        LOGGER.info("La estrategia activa es '{}' (versión {}).", catalog.activeVersion().name(),
                catalog.activeVersion().version());
    }
}
