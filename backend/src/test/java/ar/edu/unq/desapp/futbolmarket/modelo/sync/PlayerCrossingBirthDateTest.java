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
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Side;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

/**
 * La página de jugador de FR-019a y FR-019d (research D14; HU1, escenarios 9 y 24 a 29), con los
 * casos verificados el 2026-10-15. El partido es el Everton - Manchester United del 29/08/2026,
 * y el jugador de WhoScored de local es del Everton.
 */
class PlayerCrossingBirthDateTest {

    private static final LocalDate GARRIDO_BIRTH = LocalDate.of(2001, 4, 10);
    private static final LocalDate AMOUGOU_BIRTH = LocalDate.of(2006, 1, 18);
    private static final LocalDate SAKA_BIRTH = LocalDate.of(2006, 9, 20);

    private static final Player GARRIDO = player("9101", "Rafael Garrido", EVERTON, GARRIDO_BIRTH);
    private static final Player AMOUGOU = player("9102", "Mathis Amougou", EVERTON, AMOUGOU_BIRTH);

    private static final LocalDate VITINHA_BIRTH = LocalDate.of(2000, 2, 13);
    private static final int VITINHA_AGE = 26;
    private static final Player VITINHA = player("9109", "Vitinha", BRENTFORD, VITINHA_BIRTH);
    private static final Player VITINHA_TEAMMATE = player("9110", "Rodrigo Vitinha", EVERTON,
            LocalDate.of(2000, 6, 1));

    private static final LocalDate GIMENEZ_BIRTH = LocalDate.of(1995, 1, 20);
    private static final int GIMENEZ_AGE = 31;
    private static final Player GIMENEZ = player("9120", "José Giménez", BRENTFORD, GIMENEZ_BIRTH);

    private static final LocalDate FILLER_BIRTH = LocalDate.of(2004, 1, 1);
    private static final LocalDate RAFITA_BIRTH = LocalDate.of(2004, 11, 9);
    private static final int RAFITA_AGE = 21;
    private static final ReportedPlayerProfile RAFITA_PROFILE =
            new ReportedPlayerProfile(RAFITA_BIRTH, "Rafael Garrido Hierro");

    private final Match evertonManUtd = evertonManUtd();

    @Test
    void unApodoSinNingunaPalabraEnComunSeCruzaPorLaFechaDeNacimiento() {
        PlayerCrossing crossing = withProfiles(Map.of("500201", new ReportedPlayerProfile(GARRIDO_BIRTH,
                "Rafael Garrido Ruiz")), GARRIDO, AMOUGOU);

        Optional<Player> crossed = crossing.cross(appearance("500201", "Rafita", 25, Side.HOME));

        Player expected = GARRIDO.crossWith("500201");
        assertThat(crossed).contains(expected);
        assertThat(crossing.birthDateCrosses()).containsExactly(new BirthDateCross("Rafita", expected, GARRIDO_BIRTH));
        assertThat(crossing.nameCrosses()).isEmpty();
        assertThat(crossing.needsBirthDate()).isEmpty();
    }

    @Test
    void dosJugadoresConElMismoNombreDePilaYLaMismaEdadPeroOtraFechaNoSeCruzan() {
        PlayerCrossing crossing = crossing(Map.of("531677", SAKA_BIRTH), GARRIDO, AMOUGOU);

        Optional<Player> crossed = crossing.cross(appearance("531677", "Mathis Saka", 20, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason).containsExactly(CrossFailure.NOT_FOUND);
        assertThat(crossing.birthDateCrosses()).isEmpty();
    }

    @Test
    void sinFechaElQueQuedaSinCruzarSePideYElCruzadoPorNombreNo() {
        Player sivera = player("9103", "Sivera", EVERTON, LocalDate.of(1996, 8, 11));
        PlayerCrossing crossing = crossing(Map.of(), GARRIDO, AMOUGOU, sivera);

        crossing.cross(appearance("500202", "Rafita", 25, Side.HOME));
        crossing.cross(appearance("500203", "Antonio Sivera", 30, Side.HOME));

        assertThat(crossing.needsBirthDate()).containsExactly("500202");
        assertThat(crossing.newlyCrossed()).containsExactly(sivera.crossWith("500203"));
    }

    @Test
    void dosJugadoresDelEquipoConLaMismaFechaNoSeCruzan() {
        Player twin = player("9104", "Pedro Garrido", EVERTON, GARRIDO_BIRTH);
        PlayerCrossing crossing = crossing(Map.of("500204", GARRIDO_BIRTH), GARRIDO, twin);

        Optional<Player> crossed = crossing.cross(appearance("500204", "Rafita", 25, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason).containsExactly(CrossFailure.NOT_FOUND);
    }

    @Test
    void unJugadorDelCatalogoSinFechaDeNacimientoNuncaEsCandidato() {
        Player withoutBirth = player("9105", "Rafael Garrido", EVERTON, null);
        PlayerCrossing crossing = crossing(Map.of("500205", GARRIDO_BIRTH), withoutBirth);

        Optional<Player> crossed = crossing.cross(appearance("500205", "Rafita", 25, Side.HOME));

        assertThat(crossed).isEmpty();
    }

    /**
     * Los dos Juan Pérez tienen 26 años el día del partido: la edad no los distingue y la fecha sí.
     */
    @Test
    void dosHomonimosAmbiguosPorLaEdadSeResuelvenPorLaFecha() {
        Player first = player("9106", "Juan Pérez", EVERTON, LocalDate.of(2000, 2, 1));
        Player second = player("9107", "Juan Pérez", EVERTON, LocalDate.of(2000, 5, 1));
        PlayerCrossing crossing = crossing(Map.of("500206", LocalDate.of(2000, 5, 1)), first, second);

        Optional<Player> crossed = crossing.cross(appearance("500206", "Juan Pérez", 26, Side.HOME));

        assertThat(crossed).contains(second.crossWith("500206"));
    }

    /**
     * WhoScored informa una edad que no es la del catálogo, así que el nombre no alcanza en todo el
     * catálogo; la fecha y el mismo nombre sí.
     */
    @Test
    void unTransferidoSeEncuentraEnTodoElCatalogoPorLaFechaYElNombre() {
        LocalDate wissaBirth = LocalDate.of(1996, 9, 3);
        Player wissa = player("9108", "Yoane Wissa", BRENTFORD, wissaBirth);
        PlayerCrossing crossing = crossing(Map.of("500207", wissaBirth), wissa);

        Optional<Player> crossed = crossing.cross(appearance("500207", "Yoane Wissa", 35, Side.AWAY));

        assertThat(crossed).contains(wissa.crossWith("500207"));
    }

    @Test
    void unJugadorYaCruzadoConOtroIdNoEsCandidato() {
        PlayerCrossing crossing = crossing(Map.of("500208", GARRIDO_BIRTH), GARRIDO.crossWith("777777"));

        Optional<Player> crossed = crossing.cross(appearance("500208", "Rafita", 25, Side.HOME));

        assertThat(crossed).isEmpty();
    }

    @Test
    void unJugadorYaElegidoEnElPartidoNoEsCandidato() {
        PlayerCrossing crossing = crossing(Map.of("500209", GARRIDO_BIRTH, "500210", GARRIDO_BIRTH), GARRIDO);
        crossing.cross(appearance("500209", "Rafael", 25, Side.HOME));

        Optional<Player> second = crossing.cross(appearance("500210", "Rafa", 25, Side.HOME));

        assertThat(second).isEmpty();
        assertThat(crossing.newlyCrossed()).containsExactly(GARRIDO.crossWith("500209"));
    }

    /**
     * El catálogo tiene un solo Vitinha, de otro equipo, y el del partido tiene su misma edad pero
     * no está en el catálogo. Su compañero Rodrigo Vitinha, de su edad, lo cruzaría por los
     * niveles (HU1, escenario 27).
     */
    @Test
    void elCandidatoDeOtroEquipoPorNombreYEdadSinPaginaQuedaSinConfirmar() {
        PlayerCrossing crossing = withProfiles(Map.of(), VITINHA, VITINHA_TEAMMATE);

        Optional<Player> crossed = crossing.cross(appearance("407946", "Vitinha", VITINHA_AGE, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason)
                .containsExactly(CrossFailure.NOT_CONFIRMED);
        assertThat(crossing.needsBirthDate()).containsExactly("407946");
    }

    @Test
    void elCandidatoDeOtroEquipoConOtraFechaEnLaPaginaNoSeCruza() {
        PlayerCrossing crossing = withProfiles(Map.of("407946", new ReportedPlayerProfile(LocalDate.of(2000, 3, 15),
                null)), VITINHA, VITINHA_TEAMMATE);

        Optional<Player> crossed = crossing.cross(appearance("407946", "Vitinha", VITINHA_AGE, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.newlyCrossed()).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason)
                .containsExactly(CrossFailure.NOT_CONFIRMED);
    }

    /**
     * HU1, escenario 9: el del partido es el Vitinha del catálogo, que cambió de equipo.
     */
    @Test
    void elCandidatoDeOtroEquipoConSuFechaEnLaPaginaSeCruza() {
        PlayerCrossing crossing = withProfiles(Map.of("384887", new ReportedPlayerProfile(VITINHA_BIRTH, null)),
                VITINHA, VITINHA_TEAMMATE);

        Optional<Player> crossed = crossing.cross(appearance("384887", "Vitinha", VITINHA_AGE, Side.HOME));

        Player expected = VITINHA.crossWith("384887");
        assertThat(crossed).contains(expected);
        assertThat(crossing.birthDateCrosses()).containsExactly(new BirthDateCross("Vitinha", expected, VITINHA_BIRTH));
    }

    /**
     * HU1, escenario 28: jugó para el Everton y el catálogo ya lo tiene en el Brentford, con otro
     * nombre.
     */
    @Test
    void unTransferidoConOtroNombreSeCruzaPorLaFechaYUnNivelDeNombre() {
        PlayerCrossing crossing = withProfiles(Map.of("135914", new ReportedPlayerProfile(GIMENEZ_BIRTH, null)),
                GIMENEZ);

        Optional<Player> crossed = crossing.cross(appearance("135914", "José María Giménez", GIMENEZ_AGE,
                Side.HOME));

        Player expected = GIMENEZ.crossWith("135914");
        assertThat(crossed).contains(expected);
        assertThat(crossing.birthDateCrosses())
                .containsExactly(new BirthDateCross("José María Giménez", expected, GIMENEZ_BIRTH));
    }

    @Test
    void enOtroEquipoLaFechaSinUnNombreQueCoincidaNoAlcanza() {
        Player llorente = player("9111", "Marcos Llorente", BRENTFORD, GIMENEZ_BIRTH);
        PlayerCrossing crossing = withProfiles(Map.of("135914", new ReportedPlayerProfile(GIMENEZ_BIRTH, null)),
                llorente);

        Optional<Player> crossed = crossing.cross(appearance("135914", "José María Giménez", GIMENEZ_AGE,
                Side.HOME));

        assertThat(crossed).isEmpty();
    }

    /**
     * HU1, escenario 29: el catálogo tiene a Rafa Garrido con el 01/01/2004, una fecha de relleno, y
     * su página dice 09/11/2004 y su nombre completo.
     */
    @Test
    void conUnaFechaDeRellenoSeCruzaPorElNombreCompletoYElAnio() {
        Player rafaGarrido = player("9112", "Rafa Garrido", EVERTON, FILLER_BIRTH);
        PlayerCrossing crossing = withProfiles(Map.of("508349", RAFITA_PROFILE), rafaGarrido);

        Optional<Player> crossed = crossing.cross(appearance("508349", "Rafita", RAFITA_AGE, Side.HOME));

        Player expected = rafaGarrido.crossWith("508349");
        assertThat(crossed).contains(expected);
        assertThat(crossing.fullNameCrosses())
                .containsExactly(new FullNameCross("Rafita", "Rafael Garrido Hierro", expected));
        assertThat(crossing.birthDateCrosses()).isEmpty();
    }

    @Test
    void conElNombreCompletoYOtroAnioNoSeCruza() {
        Player rafaGarrido = player("9113", "Rafa Garrido", EVERTON, LocalDate.of(2003, 1, 1));
        PlayerCrossing crossing = withProfiles(Map.of("508349", RAFITA_PROFILE), rafaGarrido);

        Optional<Player> crossed = crossing.cross(appearance("508349", "Rafita", RAFITA_AGE, Side.HOME));

        assertThat(crossed).isEmpty();
    }

    @Test
    void sinNombreCompletoEnLaPaginaLaFechaDeRellenoNoSeCruza() {
        Player rafaGarrido = player("9114", "Rafa Garrido", EVERTON, FILLER_BIRTH);
        PlayerCrossing crossing = withProfiles(Map.of("508349", new ReportedPlayerProfile(RAFITA_BIRTH, null)),
                rafaGarrido);

        Optional<Player> crossed = crossing.cross(appearance("508349", "Rafita", RAFITA_AGE, Side.HOME));

        assertThat(crossed).isEmpty();
    }

    @Test
    void dosCompanerosDelMismoAnioDentroDelNombreCompletoNoSeCruzan() {
        Player rafaGarrido = player("9115", "Rafa Garrido", EVERTON, FILLER_BIRTH);
        Player rafaelGarrido = player("9116", "Rafael Garrido", EVERTON, LocalDate.of(2004, 7, 7));
        PlayerCrossing crossing = withProfiles(Map.of("508349", RAFITA_PROFILE), rafaGarrido, rafaelGarrido);

        Optional<Player> crossed = crossing.cross(appearance("508349", "Rafita", RAFITA_AGE, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.fullNameCrosses()).isEmpty();
    }

    /**
     * El compañero que nació el mismo día le gana al Vitinha de otro equipo con esa fecha.
     */
    @Test
    void laFechaEnSuEquipoLeGanaALaFechaEnOtroEquipo() {
        Player sameDayTeammate = player("9117", "Vitinha Costa", EVERTON, VITINHA_BIRTH);
        PlayerCrossing crossing = withProfiles(Map.of("384887", new ReportedPlayerProfile(VITINHA_BIRTH, null)),
                VITINHA, sameDayTeammate);

        Optional<Player> crossed = crossing.cross(appearance("384887", "Vitinha", VITINHA_AGE, Side.HOME));

        assertThat(crossed).contains(sameDayTeammate.crossWith("384887"));
    }

    /**
     * Dos José Giménez de otros equipos nacieron ese día: no se elige entre ellos, ni se pasa al
     * compañero Vargas, del mismo año y dentro del nombre completo pero de otra edad, que solo
     * podría cruzar el paso 3.
     */
    @Test
    void dosDeOtrosEquiposConEsaFechaNoSeCruzanNiPasanAlNombreCompleto() {
        Player otherGimenez = player("9118", "José Giménez", MAN_UTD, GIMENEZ_BIRTH);
        Player teammate = player("9119", "Vargas", EVERTON, LocalDate.of(1995, 12, 12));
        PlayerCrossing crossing = withProfiles(Map.of("135914", new ReportedPlayerProfile(GIMENEZ_BIRTH,
                "José María Giménez de Vargas")), GIMENEZ, otherGimenez, teammate);

        Optional<Player> crossed = crossing.cross(appearance("135914", "José María Giménez", GIMENEZ_AGE,
                Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.newlyCrossed()).isEmpty();
    }

    /**
     * Ciervo no está en el catálogo y nació el mismo día que su compañero Van Der Brempt
     * (verificado el 2026-10-15; HU1, escenario 30).
     */
    @Test
    void unCompaneroNacidoElMismoDiaSinRelacionDeNombreNoSeCruza() {
        LocalDate sameDay = LocalDate.of(2002, 4, 1);
        Player vanDerBrempt = player("9121", "Ignace Van Der Brempt", EVERTON, sameDay);
        PlayerCrossing crossing = withProfiles(Map.of("407149", new ReportedPlayerProfile(sameDay, null)),
                vanDerBrempt);

        Optional<Player> crossed = crossing.cross(appearance("407149", "Riccardo Ciervo", 24, Side.HOME));

        assertThat(crossed).isEmpty();
        assertThat(crossing.uncrossed()).extracting(UncrossedPlayer::reason).containsExactly(CrossFailure.NOT_FOUND);
    }

    @Test
    void conLaMismaFechaAlcanzaElNombreDePila() {
        LocalDate birthDate = LocalDate.of(2004, 3, 8);
        Player issahaku = player("9122", "Abdul Issahaku", EVERTON, birthDate);
        PlayerCrossing crossing = withProfiles(Map.of("430985", new ReportedPlayerProfile(birthDate, null)),
                issahaku);

        Optional<Player> crossed = crossing.cross(appearance("430985", "Abdul Fatawu", 22, Side.HOME));

        assertThat(crossed).contains(issahaku.crossWith("430985"));
    }

    /**
     * WhoScored informa una edad que no es la del catálogo, así que el nivel 5 no lo cruza; con la
     * fecha, el nivel sin la edad es la relación de nombre.
     */
    @Test
    void conLaMismaFechaAlcanzaUnNivelSinLaEdad() {
        LocalDate birthDate = LocalDate.of(1997, 6, 10);
        Player alTaamari = player("9123", "Musa Al Taamari", EVERTON, birthDate);
        PlayerCrossing crossing = withProfiles(Map.of("300001", new ReportedPlayerProfile(birthDate, null)),
                alTaamari);

        Optional<Player> crossed = crossing.cross(appearance("300001", "Mousa Al Tamari", 35, Side.HOME));

        assertThat(crossed).contains(alTaamari.crossWith("300001"));
    }

    @Test
    void conLaMismaFechaAlcanzaElNombreDentroDelNombreCompleto() {
        LocalDate birthDate = LocalDate.of(1998, 6, 28);
        Player goncalves = player("9124", "Pedro Gonçalves", EVERTON, birthDate);
        PlayerCrossing crossing = withProfiles(Map.of("344378", new ReportedPlayerProfile(birthDate,
                "Pedro António Pereira Gonçalves")), goncalves);

        Optional<Player> crossed = crossing.cross(appearance("344378", "Pote", 28, Side.HOME));

        assertThat(crossed).contains(goncalves.crossWith("344378"));
    }

    /**
     * Cada fecha es la de una página de jugador sin nombre completo.
     */
    private PlayerCrossing crossing(Map<String, LocalDate> birthDates, Player... players) {
        Map<String, ReportedPlayerProfile> profiles = birthDates.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> new ReportedPlayerProfile(entry.getValue(), null)));
        return withProfiles(profiles, players);
    }

    private PlayerCrossing withProfiles(Map<String, ReportedPlayerProfile> profiles, Player... players) {
        return new PlayerCrossing(CatalogPlayers.of(List.of(players)), evertonManUtd, EVERTON_MAN_UTD_STATS,
                profiles);
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
