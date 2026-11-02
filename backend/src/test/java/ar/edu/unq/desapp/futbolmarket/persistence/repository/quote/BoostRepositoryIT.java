package ar.edu.unq.desapp.futbolmarket.persistence.repository.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.PlayerBoost;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteWeek;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.WeekBoosts;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.quote.BoostSQLDAO;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;

/**
 * Los potenciados contra la base: se leen por semana en el orden elegido y reemplazar una semana no
 * toca las demás.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:boost-repository-it;DB_CLOSE_DELAY=-1")
class BoostRepositoryIT {
    private static final QuoteWeek WEEK = new QuoteWeek(LocalDate.of(2026, 11, 2));

    @Autowired
    private BoostRepository boostRepository;

    @Autowired
    private BoostSQLDAO boostDAO;

    @Autowired
    private ApplicationContext context;

    private QuoteDatabase database;
    private List<Player> players;

    @BeforeEach
    void prepareDatabase() {
        database = new QuoteDatabase(context);
        cleanDatabase();
        Team everton = database.team("62", "Everton FC");
        players = List.of(
                database.player("8135", "Iliman Ndiaye", Position.FORWARD, everton),
                database.player("8136", "Beto", Position.FORWARD, everton),
                database.player("8137", "Jack Grealish", Position.MIDFIELDER, everton),
                database.player("8138", "James Tarkowski", Position.DEFENDER, everton),
                database.player("8139", "Jordan Pickford", Position.GOALKEEPER, everton));
    }

    @AfterEach
    void cleanDatabase() {
        boostDAO.deleteAll();
        database.clean();
    }

    @Test
    void unaSemanaSinFilasNoTienePotenciados() {
        WeekBoosts week = boostRepository.findWeek(WEEK);

        assertThat(week).isEqualTo(WeekBoosts.none(WEEK));
    }

    @Test
    void guardaYLeeLosPotenciadosEnElOrdenElegido() {
        WeekBoosts chosen = boosts(WEEK, 3, 0, 2, 1);

        WeekBoosts saved = boostRepository.replace(chosen);

        assertThat(saved).isEqualTo(chosen);
        assertThat(boostRepository.findWeek(WEEK)).isEqualTo(chosen);
    }

    @Test
    void reemplazarConLosMismosJugadoresEnOtroOrdenNoChocaConElIndiceUnico() {
        boostRepository.replace(boosts(WEEK, 0, 1, 2, 3));
        WeekBoosts reordered = boosts(WEEK, 3, 2, 1, 0);

        WeekBoosts saved = boostRepository.replace(reordered);

        assertThat(saved).isEqualTo(reordered);
        assertThat(boostDAO.count()).isEqualTo(4);
    }

    @Test
    void vaciarUnaSemanaNoTocaLasDemas() {
        boostRepository.replace(boosts(WEEK, 0, 1, 2, 3));
        boostRepository.replace(boosts(WEEK.next(), 1, 2, 3, 4));

        boostRepository.replace(WeekBoosts.none(WEEK));

        assertThat(boostRepository.findWeek(WEEK).isEmpty()).isTrue();
        assertThat(boostRepository.findAllByWeek()).containsOnlyKeys(WEEK.next());
        assertThat(boostRepository.findAllByWeek().get(WEEK.next())).isEqualTo(boosts(WEEK.next(), 1, 2, 3, 4));
    }

    /**
     * Cuatro de los jugadores, por su índice, con 200, 250, 300 y 400 %.
     */
    private WeekBoosts boosts(QuoteWeek week, int first, int second, int third, int fourth) {
        return new WeekBoosts(week, List.of(
                new PlayerBoost(players.get(first).id(), 200), new PlayerBoost(players.get(second).id(), 250),
                new PlayerBoost(players.get(third).id(), 300), new PlayerBoost(players.get(fourth).id(), 400)));
    }
}
