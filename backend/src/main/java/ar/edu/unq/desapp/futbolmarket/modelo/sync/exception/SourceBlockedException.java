package ar.edu.unq.desapp.futbolmarket.modelo.sync.exception;

import ar.edu.unq.desapp.futbolmarket.modelo.sync.BlockedPage;

/**
 * WhoScored rechazó dos veces seguidas la misma página: la corrida se corta y no se piden más
 * páginas (FR-043 y FR-044).
 *
 * <p>Nunca llega a una respuesta HTTP: la sincronización la captura y la vuelve un corte en el
 * informe.</p>
 */
public class SourceBlockedException extends RuntimeException {
    private final transient BlockedPage page;

    public SourceBlockedException(BlockedPage page) {
        super("WhoScored rechazó dos veces seguidas la página %s.".formatted(page.path()));
        this.page = page;
    }

    public BlockedPage page() {
        return page;
    }
}
