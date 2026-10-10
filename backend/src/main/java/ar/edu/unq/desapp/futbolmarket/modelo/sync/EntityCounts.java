package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Cuántos datos de un tipo se crearon, se actualizaron y se omitieron en una liga. Un dato que
 * vuelve a llegar cuenta como actualizado, haya cambiado o no.
 */
public record EntityCounts(int created, int updated, int skipped) {
    public static EntityCounts none() {
        return new EntityCounts(0, 0, 0);
    }
}
