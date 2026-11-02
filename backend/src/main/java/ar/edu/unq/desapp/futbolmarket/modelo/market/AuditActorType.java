package ar.edu.unq.desapp.futbolmarket.modelo.market;

/**
 * Quién causó un hecho de la auditoría: el usuario que hizo el pedido, el superusuario con sus
 * órdenes iniciales o el sistema, que cancela por saldo insuficiente.
 */
public enum AuditActorType {
    USER,
    SUPERUSER,
    SYSTEM
}
