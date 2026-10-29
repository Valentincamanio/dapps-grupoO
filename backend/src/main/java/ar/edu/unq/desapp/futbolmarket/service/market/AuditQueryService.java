package ar.edu.unq.desapp.futbolmarket.service.market;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.AuditRepository;
import lombok.RequiredArgsConstructor;

/**
 * La consulta de la auditoría del mercado (FR-053). Solo lee: los registros los escriben, en la
 * misma transacción que cada hecho, la creación de órdenes iniciales y los pedidos del mercado.
 *
 * <p>Solo la consulta el administrador, y eso lo decide la cadena de seguridad antes del controller
 * (research D24), así que acá no se recibe a quien pide.</p>
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {
    private final AuditRepository auditRepository;

    /**
     * Una hoja de los registros que cumplen el filtro, del más nuevo al más viejo.
     */
    @Transactional(readOnly = true)
    public QuotePage<AuditEntry> audit(AuditFilter filter, int page, int size) {
        return auditRepository.findAudit(filter, page, size);
    }
}
