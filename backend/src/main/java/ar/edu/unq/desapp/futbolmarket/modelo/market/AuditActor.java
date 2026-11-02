package ar.edu.unq.desapp.futbolmarket.modelo.market;

import ar.edu.unq.desapp.futbolmarket.modelo.market.exception.MarketInvariantException;

/**
 * El autor de un hecho de la auditoría. Solo el sistema no tiene id: cancela por saldo
 * insuficiente sin que nadie lo pida.
 */
public record AuditActor(AuditActorType type, Long id) {

    public AuditActor {
        requireValid(type, id);
    }

    public static AuditActor user(long userId) {
        return new AuditActor(AuditActorType.USER, userId);
    }

    public static AuditActor superuser(long superuserId) {
        return new AuditActor(AuditActorType.SUPERUSER, superuserId);
    }

    public static AuditActor system() {
        return new AuditActor(AuditActorType.SYSTEM, null);
    }

    /**
     * La regla también la aplica {@link AuditEntry}, que guarda el tipo y el id por separado.
     */
    static void requireValid(AuditActorType type, Long id) {
        if (type == null || (type == AuditActorType.SYSTEM) != (id == null)) {
            throw new MarketInvariantException("Solo el sistema actúa sin id: " + type + " con id " + id + ".");
        }
    }
}
