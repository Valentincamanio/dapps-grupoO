package ar.edu.unq.desapp.futbolmarket.service.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditActorType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEventType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyState;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidQuoteDateException;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.exception.InvalidDateRangeException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.AuditRepository;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {
    private static final long OWNER = 7L;
    private static final long PLAYER = 12L;

    @Mock
    private AuditRepository auditRepository;

    @InjectMocks
    private AuditQueryService service;

    @Test
    void laConsultaEsLaHojaDelRepositoryConElMismoFiltroYLaMismaPaginacion() {
        AuditFilter filter = AuditFilter.of(AuditEventType.ORDER_CREATED, OWNER, PLAYER, "2026-10-01", "2026-10-31");
        QuotePage<AuditEntry> page = new QuotePage<>(List.of(created(Instant.parse("2026-10-28T15:00:00Z"))), 1, 2, 3);
        when(auditRepository.findAudit(filter, 1, 2)).thenReturn(page);

        QuotePage<AuditEntry> audit = service.audit(filter, 1, 2);

        assertThat(audit).isSameAs(page);
        verify(auditRepository).findAudit(filter, 1, 2);
        verifyNoMoreInteractions(auditRepository);
    }

    @Test
    void sinRegistrosLaHojaVieneVacia() {
        QuotePage<AuditEntry> empty = new QuotePage<>(List.of(), 0, 10, 0);
        when(auditRepository.findAudit(AuditFilter.none(), 0, 10)).thenReturn(empty);

        QuotePage<AuditEntry> audit = service.audit(AuditFilter.none(), 0, 10);

        assertThat(audit.content()).isEmpty();
        assertThat(audit.totalElements()).isZero();
    }

    @Test
    void unRangoInvertidoSeRechazaAlArmarElFiltroSinConsultarNada() {
        assertThatThrownBy(() -> service.audit(AuditFilter.of(null, null, null, "2026-09-10", "2026-09-01"), 0, 10))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessage("El inicio del rango (2026-09-10) es posterior al final (2026-09-01).");

        verifyNoInteractions(auditRepository);
    }

    @Test
    void unaFechaInvalidaSeRechazaRepitiendoElValor() {
        assertThatThrownBy(() -> service.audit(AuditFilter.of(null, null, null, "2026-02-30", null), 0, 10))
                .isInstanceOf(InvalidQuoteDateException.class)
                .hasMessage("La fecha '2026-02-30' no es válida: use el formato AAAA-MM-DD.");

        verifyNoInteractions(auditRepository);
    }

    @Test
    void elFiltroIncluyeLosDosDiasDelRangoEnHoraDeArgentina() {
        AuditFilter filter = AuditFilter.of(null, null, null, "2026-10-28", "2026-10-29");

        assertThat(filter.fromInstant()).isEqualTo(Instant.parse("2026-10-28T03:00:00Z"));
        assertThat(filter.toExclusive()).isEqualTo(Instant.parse("2026-10-30T03:00:00Z"));
        assertThat(AuditFilter.none().fromInstant()).isNull();
        assertThat(AuditFilter.none().toExclusive()).isNull();
    }

    private static AuditEntry created(Instant at) {
        PartyChange change = new PartyChange(
                new PartyState(OWNER, new BigDecimal("1000.00"), new BigDecimal("0.00"), 0),
                new PartyState(OWNER, new BigDecimal("989.70"), new BigDecimal("10.30"), 0));
        return new AuditEntry(1L, at, AuditEventType.ORDER_CREATED, AuditActorType.USER, OWNER, PLAYER, 5L, null,
                OrderSide.BUY, 10, null, null, null, null, change, null, "Orden de compra");
    }
}
