package ar.edu.unq.desapp.futbolmarket.modelo.season;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;

class CurrentSeasonsTest {

    private static final Season PREMIER_2025 = season(1L, "2400", League.PREMIER, 2025);
    private static final Season PREMIER_2026 = season(2L, "2502", League.PREMIER, 2026);
    private static final Season LA_LIGA_2025 = season(3L, "2401", League.LA_LIGA, 2025);
    private static final Season LA_LIGA_2026 = season(4L, "2503", League.LA_LIGA, 2026);

    @Test
    void laTemporadaEnCursoDeCadaLigaEsLaDeInicioMasReciente() {
        CurrentSeasons seasons = CurrentSeasons.of(List.of(PREMIER_2026, LA_LIGA_2025, PREMIER_2025, LA_LIGA_2026));

        Optional<Season> premier = seasons.current(League.PREMIER);
        Optional<Season> laLiga = seasons.current(League.LA_LIGA);

        assertThat(premier).contains(PREMIER_2026);
        assertThat(laLiga).contains(LA_LIGA_2026);
    }

    @Test
    void unaLigaSinTemporadasNoTieneTemporadaEnCurso() {
        CurrentSeasons seasons = CurrentSeasons.of(List.of(PREMIER_2026));

        Optional<Season> bundesliga = seasons.current(League.BUNDESLIGA);

        assertThat(bundesliga).isEmpty();
    }

    @Test
    void unaTemporadaNuevaReemplazaALaAnterior() {
        CurrentSeasons beforeTheChange = CurrentSeasons.of(List.of(PREMIER_2025));
        CurrentSeasons afterTheChange = CurrentSeasons.of(List.of(PREMIER_2025, PREMIER_2026));

        Optional<Season> before = beforeTheChange.current(League.PREMIER);
        Optional<Season> after = afterTheChange.current(League.PREMIER);

        assertThat(before).contains(PREMIER_2025);
        assertThat(after).contains(PREMIER_2026);
    }

    private static Season season(Long id, String externalId, League league, int startYear) {
        return new Season(id, externalId, league, LocalDate.of(startYear, 8, 15), LocalDate.of(startYear + 1, 5, 24), 1);
    }
}
