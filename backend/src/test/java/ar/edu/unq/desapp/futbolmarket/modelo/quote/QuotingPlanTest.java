package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.ARSENAL;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.LIVERPOOL;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.quote.QuoteFixtures.WEEK;
import static org.assertj.core.api.Assertions.assertThat;

class QuotingPlanTest {
    private static final QuoteWeek W1 = WEEK;
    private static final QuoteWeek W2 = W1.next();
    private static final QuoteWeek W3 = W2.next();
    private static final QuoteWeek W4 = W3.next();

    /**
     * El miércoles de la cuarta semana: hay tres semanas cerradas.
     */
    private static final Instant DURING_W4 = W4.start().plus(Duration.ofDays(2));

    @Test
    void sinCotizacionesEmpiezaEnLaPrimeraSemanaConEstadisticas() {
        var matches = List.of(withStats(W1), withStats(W2), withStats(W3));

        var plan = QuotingPlan.of(Optional.empty(), Optional.of(W1), matches, DURING_W4);

        assertThat(plan.weeksToQuote()).containsExactly(W1, W2, W3);
        assertThat(plan.pendingWeeks()).isEmpty();
        assertThat(plan.hasClosedUnquotedWeeks()).isTrue();
    }

    @Test
    void conCotizacionesEmpiezaEnLaSiguienteALaUltimaCotizada() {
        var plan = QuotingPlan.of(Optional.of(W1), Optional.of(W1), List.of(withStats(W1), withStats(W2)),
                DURING_W4);

        assertThat(plan.weeksToQuote()).containsExactly(W2, W3);
    }

    @Test
    void sinEstadisticasEnNingunaParteNoHayNadaParaCotizar() {
        var plan = QuotingPlan.of(Optional.empty(), Optional.empty(), List.of(), DURING_W4);

        assertThat(plan.decisions()).isEmpty();
        assertThat(plan.weeksToQuote()).isEmpty();
        assertThat(plan.hasClosedUnquotedWeeks()).isFalse();
    }

    @Test
    void laSemanaEnCursoNuncaSeCotiza() {
        var plan = QuotingPlan.of(Optional.empty(), Optional.of(W4), List.of(withStats(W4)), DURING_W4);

        assertThat(plan.decisions()).isEmpty();
    }

    @Test
    void unaSemanaSeCotizaRecienDesdeElLunesALasCeroDeLaSiguiente() {
        var justBefore = QuotingPlan.of(Optional.empty(), Optional.of(W1), List.of(), W1.end().minusSeconds(1));
        var atClose = QuotingPlan.of(Optional.empty(), Optional.of(W1), List.of(), W1.end());

        assertThat(justBefore.weeksToQuote()).isEmpty();
        assertThat(atClose.weeksToQuote()).containsExactly(W1);
    }

    @Test
    void unPartidoTerminadoSinEstadisticasAMenosDeSieteDiasDejaLaSemanaPendiente() {
        var matches = List.of(withStats(W1), withoutStats(W1));
        var sixDaysAfterClose = W1.end().plus(Duration.ofDays(6));

        var plan = QuotingPlan.of(Optional.empty(), Optional.of(W1), matches, sixDaysAfterClose);

        assertThat(plan.weeksToQuote()).isEmpty();
        assertThat(plan.pendingWeeks()).containsExactly(new PendingWeek(W1, PendingReason.MISSING_STATS));
        assertThat(plan.hasClosedUnquotedWeeks()).isTrue();
    }

    @Test
    void unaSemanaPendienteDejaPendientesALasSiguientes() {
        var matches = List.of(withStats(W1), withoutStats(W2), withStats(W3));
        var longerWait = Duration.ofDays(14);

        var plan = QuotingPlan.of(Optional.empty(), Optional.of(W1), matches, DURING_W4, longerWait);

        assertThat(plan.weeksToQuote()).containsExactly(W1);
        assertThat(plan.pendingWeeks()).containsExactly(
                new PendingWeek(W2, PendingReason.MISSING_STATS),
                new PendingWeek(W3, PendingReason.PREVIOUS_WEEK_PENDING));
    }

    @Test
    void aLosSieteDiasDelCierreSeCotizaConLoQueHaya() {
        var matches = List.of(withStats(W1), withoutStats(W1));
        var sevenDaysAfterClose = W1.end().plus(QuotingPlan.STATS_WAIT);

        var plan = QuotingPlan.of(Optional.empty(), Optional.of(W1), matches, sevenDaysAfterClose);

        assertThat(plan.weeksToQuote()).containsExactly(W1, W2);
        assertThat(plan.pendingWeeks()).isEmpty();
    }

    @Test
    void unaSemanaSinPartidosSeCotiza() {
        var plan = QuotingPlan.of(Optional.of(W1), Optional.of(W1), List.of(withStats(W1)), DURING_W4);

        assertThat(plan.weeksToQuote()).containsExactly(W2, W3);
    }

    @Test
    void unPartidoSinEstadisticasDeUnaSemanaYaCotizadaNoBloqueaNada() {
        var plan = QuotingPlan.of(Optional.of(W2), Optional.of(W1), List.of(withoutStats(W2)),
                W3.end().plus(Duration.ofHours(1)));

        assertThat(plan.weeksToQuote()).containsExactly(W3);
    }

    @Test
    void conTodoCotizadoNoQuedaNadaPorCotizar() {
        var plan = QuotingPlan.of(Optional.of(W3), Optional.of(W1), List.of(withStats(W1), withStats(W3)),
                DURING_W4);

        assertThat(plan.decisions()).isEmpty();
        assertThat(plan.hasClosedUnquotedWeeks()).isFalse();
    }

    @Test
    void lasDecisionesVanEnOrdenCronologico() {
        var plan = QuotingPlan.of(Optional.empty(), Optional.of(W1), List.of(withoutStats(W3)),
                W3.end().plus(Duration.ofDays(1)));

        assertThat(plan.decisions()).extracting(WeekDecision::week).containsExactly(W1, W2, W3);
        assertThat(plan.decisions()).extracting(WeekDecision::isPending).containsExactly(false, false, true);
    }

    private static Match withStats(QuoteWeek week) {
        return QuoteFixtures.finishedMatch("w-" + week.monday(), week, EVERTON, MAN_UTD, 1, 0);
    }

    private static Match withoutStats(QuoteWeek week) {
        return QuoteFixtures.finishedMatchWithoutStats("p-" + week.monday(), week, ARSENAL, LIVERPOOL);
    }
}
