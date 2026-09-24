package ar.edu.unq.desapp.futbolmarket.config;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.League;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Player;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Position;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Team;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.repository.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.repository.TeamRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@Profile("local")
public class PlayerCatalogDataSeeder implements ApplicationRunner {
    private static final String DATASET_PATH = "data/players.json";
    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    public PlayerCatalogDataSeeder(
            PlayerRepository playerRepository,
            TeamRepository teamRepository
    ) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        for (PlayerSeed playerSeed : readPlayers()) {
            if (!playerRepository.existsByExternalId(playerSeed.externalId())) {
                Team team = teamRepository.findOrCreate(playerSeed.team(), playerSeed.league());
                playerRepository.save(new Player(
                        playerSeed.externalId(), playerSeed.name(), playerSeed.position(), team
                ));
            }
        }
    }

    private List<PlayerSeed> readPlayers() throws IOException {
        try (InputStream inputStream = new ClassPathResource(DATASET_PATH).getInputStream()) {
            String dataset = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParserFactory.getJsonParser().parseList(dataset).stream()
                    .map(this::toPlayerSeed)
                    .toList();
        }
    }

    private PlayerSeed toPlayerSeed(Object player) {
        if (!(player instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("El dataset de jugadores contiene un registro inválido.");
        }
        return new PlayerSeed(
                field(values, "externalId"),
                field(values, "name"),
                Position.valueOf(field(values, "position")),
                field(values, "team"),
                League.valueOf(field(values, "league"))
        );
    }

    private String field(Map<?, ?> values, String fieldName) {
        Object value = values.get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException("El dataset de jugadores no contiene el campo " + fieldName + ".");
        }
        return value.toString();
    }

    private record PlayerSeed(String externalId, String name, Position position, String team, League league) {
    }
}
