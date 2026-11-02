package ar.edu.unq.desapp.futbolmarket.modelo.market;

/**
 * El tipo de usuario dueño de una orden, y la contraparte de una operación. Solo el
 * superusuario tiene órdenes {@code ADMIN}: sus órdenes iniciales.
 */
public enum OwnerType {
    USER,
    ADMIN
}
