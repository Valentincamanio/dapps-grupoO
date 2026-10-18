package ar.edu.unq.desapp.futbolmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import ar.edu.unq.desapp.futbolmarket.service.sync.StatsSnapshotService;

/**
 * Exporta el snapshot de estadísticas de WhoScored desde la base local y termina (research D21).
 * La corre {@code ./gradlew exportStatsSnapshot}, desde {@code backend/} y con la aplicación
 * detenida: no es un recurso de la aplicación y no tiene endpoint (FR-028).
 *
 * <p>Levanta Spring con el perfil {@code local}, sin servidor web y sin sincronizaciones. Esos
 * valores van como argumentos de la línea de comandos, que pisan cualquier otra fuente; los
 * argumentos que recibe {@code main} no se usan. Termina con código 0, o con 1 si el snapshot
 * vigente existe y no se pudo leer, en cuyo caso no lo pisa.</p>
 */
public final class StatsSnapshotExport {
    private static final int EXPORTED = 0;
    private static final int UNREADABLE_SNAPSHOT = 1;
    private static final String[] FIXED_ARGUMENTS = {
            "--spring.profiles.active=local",
            "--futbolmarket.football-data.sync.on-startup=false",
            "--futbolmarket.football-data.sync.cron=-"
    };

    private StatsSnapshotExport() {
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(FutbolMarketApplication.class)
                .web(WebApplicationType.NONE)
                .run(FIXED_ARGUMENTS);
        boolean exported;
        try {
            exported = context.getBean(StatsSnapshotService.class).export();
        } finally {
            SpringApplication.exit(context);
        }
        System.exit(exported ? EXPORTED : UNREADABLE_SNAPSHOT);
    }
}
