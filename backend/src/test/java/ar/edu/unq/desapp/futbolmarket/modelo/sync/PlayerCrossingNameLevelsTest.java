package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.BRENTFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON_MAN_UTD_STATS;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.evertonManUtd;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Side;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * Los niveles de nombre de FR-019b y la edad de FR-019c en {@link PlayerCrossing}, con los ejemplos
 * reales de research D14 (HU1, escenarios 21 a 23). El partido es el Everton - Manchester United del
 * 29/08/2026: las edades de los comentarios son las de ese día.
 */
class PlayerCrossingNameLevelsTest {

    private static final Player SIVERA = player("9001", "Sivera", EVERTON, LocalDate.of(1996, 8, 11));
    private static final Player KANG_IN_LEE = player("9002", "Kang-in Lee", EVERTON, LocalDate.of(2001, 2, 19));
    private static final Player BAENA = player("9003", "Alejandro Baena", EVERTON, LocalDate.of(2001, 7, 20));
    private static final Player NDICKA = player("9004", "Evan N'Dicka", EVERTON, LocalDate.of(1999, 8, 20));
    private static final Player YARMOLYUK = player("9005", "Yegor Yarmolyuk", EVERTON, LocalDate.of(2004, 3, 1));
    private static final Player ISSAHAKU = player("9006", "Abdul Issahaku", EVERTON, LocalDate.of(2004, 3, 4));
    private static final Player FEMENIA = player("9007", "Femenía Far", EVERTON, LocalDate.of(1991, 5, 2));

    private static final int SIVERA_AGE = 30;
    private static final int LEE_AND_BAENA_AGE = 25;
    private static final int NDICKA_AGE = 27;
    private static final int YARMOLYUK_AND_ISSAHAKU_AGE = 22;
    private static final int FEMENIA_AGE = 35;

    private final Match evertonManUtd = evertonManUtd();

    static Stream<Arguments> oneExamplePerLevel() {
        return Stream.of(
                Arguments.of("Lee Kang-In", LEE_AND_BAENA_AGE, KANG_IN_LEE, NameMatchLevel.SAME_WORDS),
                Arguments.of("Antonio Sivera", SIVERA_AGE, SIVERA, NameMatchLevel.CONTAINED_WORDS),
                Arguments.of("Álex Baena", LEE_AND_BAENA_AGE, BAENA, NameMatchLevel.SAME_SURNAME),
                Arguments.of("Evan Ndicka", NDICKA_AGE, NDICKA, NameMatchLevel.SAME_COMPACT),
                Arguments.of("Yehor Yarmoliuk", YARMOLYUK_AND_ISSAHAKU_AGE, YARMOLYUK, NameMatchLevel.SIMILAR_SURNAME),
                Arguments.of("Kiko Femenía", FEMENIA_AGE, FEMENIA, NameMatchLevel.WORD_PREFIX));
    }

    @ParameterizedTest(name = "{3}: ''{0}''")
    @MethodSource("oneExamplePerLevel")
    void cadaNivelCruzaSuEjemploYDejaElCruceConEseNivel(String reportedName, int age, Player expected,
                                                         NameMatchLevel level) {
        PlayerCrossing crossing = crossing(SIVERA, KANG_IN_LEE, BAENA, NDICKA, YARMOLYUK, ISSAHAKU, FEMENIA);

        Optional<Player> crossed = crossing.cross(appearance("500100", reportedName, age, Side.HOME));

        Player expectedCrossed = expected.crossWith("500100");
        assertThat(crossed).contains(expectedCrossed);
        assertThat(crossing.nameCrosses()).containsExactly(new NameCross(reportedName, expectedCrossed, level));
        assertThat(crossing.newlyCrossed()).containsExactly(expectedCrossed);
    }

    @Test
    void conOtraEdadNoCruzaYQuedaSinEncontrar() {
        PlayerCrossing crossing = crossing(SIVERA);

        Optional<Player> crossed = crossing.cross(appearance("500101", "Antonio Sivera", SIVERA_AGE + 2, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason).containsExactly(CrossFailure.NOT_FOUND);
        assertThat(crossing.nameCrosses()).isEmpty();
    }

    @Test
    void unJugadorSinFechaDeNacimientoNoEntraEnLosNiveles() {
        Player garrido = player("9010", "Garrido", EVERTON, null);
        PlayerCrossing crossing = crossing(garrido);

        Optional<Player> crossed = crossing.cross(appearance("500102", "Rafa Garrido", 28, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason).containsExactly(CrossFailure.NOT_FOUND);
    }

    /**
     * Los dos González tienen 28 años: el apellido no alcanza, y el nivel de comienzo de palabra, que
     * solo encontraría a Alfonso, no llega a probarse.
     */
    @Test
    void dosCandidatosEnUnNivelDanAmbiguoAunqueUnNivelSiguienteTengaUnoSolo() {
        Player fran = player("9011", "Fran González", EVERTON, LocalDate.of(1998, 1, 1));
        Player alfonso = player("9012", "Alfonso González", EVERTON, LocalDate.of(1998, 5, 5));
        PlayerCrossing crossing = crossing(fran, alfonso);

        Optional<Player> crossed = crossing.cross(appearance("500103", "Alfon González", 28, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason).containsExactly(CrossFailure.AMBIGUOUS);
        assertThat(crossing.nameCrosses()).isEmpty();
    }

    @Test
    void elNombreExactoEnElEquipoLeGanaAUnNivel() {
        Player antonioSivera = player("9013", "Antonio Sivera", EVERTON, LocalDate.of(1996, 8, 11));
        PlayerCrossing crossing = crossing(SIVERA, antonioSivera);

        Optional<Player> crossed = crossing.cross(appearance("500104", "Antonio Sivera", SIVERA_AGE, Side.HOME));

        assertThat(crossed).contains(antonioSivera.crossWith("500104"));
        assertThat(crossing.nameCrosses()).isEmpty();
    }

    /**
     * Wissa ya está en el Brentford del catálogo y juega para el Manchester United: se lo encuentra
     * en todo el catálogo por nombre exacto, y la fecha de su página lo confirma, aunque en el
     * Manchester United haya otro Wissa de su edad.
     */
    @Test
    void unTransferidoSeEncuentraPorNombreExactoAntesQueUnParecidoDeSuEquipoViejo() {
        Player wissa = player("9014", "Yoane Wissa", BRENTFORD, LocalDate.of(1996, 9, 3));
        Player otherWissa = player("9015", "Christian Wissa", MAN_UTD, LocalDate.of(1996, 12, 1));
        PlayerCrossing crossing = new PlayerCrossing(CatalogPlayers.of(List.of(wissa, otherWissa)), evertonManUtd,
                EVERTON_MAN_UTD_STATS, Map.of("500105", new ReportedPlayerProfile(wissa.dateOfBirth(), null)));

        Optional<Player> crossed = crossing.cross(appearance("500105", "Yoane Wissa", 29, Side.AWAY));

        assertThat(crossed).contains(wissa.crossWith("500105"));
        assertThat(crossing.nameCrosses()).isEmpty();
    }

    @Test
    void unJugadorYaCruzadoConOtroIdNoEsCandidatoDeNingunNivel() {
        PlayerCrossing crossing = crossing(SIVERA.crossWith("777777"));

        Optional<Player> crossed = crossing.cross(appearance("500106", "Antonio Sivera", SIVERA_AGE, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason).containsExactly(CrossFailure.NOT_FOUND);
    }

    @Test
    void unJugadorYaElegidoEnElPartidoNoEsCandidatoDeNingunNivel() {
        PlayerCrossing crossing = crossing(SIVERA);
        crossing.cross(appearance("500107", "Antonio Sivera", SIVERA_AGE, Side.HOME));

        Optional<Player> second = crossing.cross(appearance("500108", "Toni Sivera", SIVERA_AGE, Side.HOME));

        assertThat(second).isEmpty();
        assertThat(crossing.newlyCrossed()).containsExactly(SIVERA.crossWith("500107"));
    }

    @Test
    void enLosNivelesSeReconoceAQuienCumplioAniosDespuesDelPartido() {
        PlayerCrossing crossing = crossing(SIVERA);

        Optional<Player> crossed = crossing.cross(appearance("500109", "Antonio Sivera", SIVERA_AGE + 1, Side.HOME));

        assertThat(crossed).contains(SIVERA.crossWith("500109"));
    }

    /**
     * El primero tiene 25 años el día del partido y cumple 26 en septiembre, cuando se consulta la
     * página; el otro tiene 29.
     */
    @Test
    void enElDesempateEntreHomonimosSeReconoceAQuienCumplioAniosDespuesDelPartido() {
        Player younger = player("9016", "Juan Pérez", EVERTON, LocalDate.of(2000, 9, 15));
        Player older = player("9017", "Juan Pérez", EVERTON, LocalDate.of(1997, 1, 1));
        PlayerCrossing crossing = crossing(younger, older);

        Optional<Player> crossed = crossing.cross(appearance("500110", "Juan Pérez", 26, Side.HOME));

        assertThat(crossed).contains(younger.crossWith("500110"));
    }

    @Test
    void enTodoElCatalogoSeReconoceAlTransferidoQueCumplioAniosDespuesDelPartido() {
        Player wissa = player("9018", "Yoane Wissa", BRENTFORD, LocalDate.of(1996, 9, 3));
        PlayerCrossing crossing = crossing(wissa);

        Optional<Player> crossed = crossing.cross(appearance("500111", "Yoane Wissa", 30, Side.AWAY));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason)
                .containsExactly(CrossFailure.NOT_CONFIRMED);
    }

    @Test
    void dosAniosMasQueElDiaDelPartidoYaNoEsLaMismaEdad() {
        Player wissa = player("9019", "Yoane Wissa", BRENTFORD, LocalDate.of(1996, 9, 3));
        PlayerCrossing crossing = crossing(wissa);

        Optional<Player> crossed = crossing.cross(appearance("500112", "Yoane Wissa", 31, Side.AWAY));

        assertThat(crossed).isEmpty();
    }

    private PlayerCrossing crossing(Player... players) {
        return new PlayerCrossing(CatalogPlayers.of(List.of(players)), evertonManUtd, EVERTON_MAN_UTD_STATS);
    }

    private static Player player(String externalId, String name, Team team, LocalDate dateOfBirth) {
        return new Player(Long.valueOf(externalId), externalId, name, Position.MIDFIELDER, team, dateOfBirth, null,
                true);
    }

    private static ReportedAppearance appearance(String whoscoredId, String name, Integer age, Side side) {
        return new ReportedAppearance(whoscoredId, name, age, side, true, null, null, MetricCounts.empty(), null,
                false);
    }
}
