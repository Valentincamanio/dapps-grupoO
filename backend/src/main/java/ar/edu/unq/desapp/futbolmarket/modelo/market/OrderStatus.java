package ar.edu.unq.desapp.futbolmarket.modelo.market;

/**
 * El estado de una orden. No se guarda por separado de las cantidades: se deriva de ellas
 * (research D9), así nunca las contradice.
 */
public enum OrderStatus {
    PENDING,
    PARTIAL,
    EXECUTED,
    CANCELLED;

    /**
     * Si todavía tiene tokens pendientes: solo esas se cruzan y se cancelan.
     */
    public boolean isOpen() {
        return this == PENDING || this == PARTIAL;
    }
}
