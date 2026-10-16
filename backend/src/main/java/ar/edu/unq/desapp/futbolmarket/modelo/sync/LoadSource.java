package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * De dónde salieron las estadísticas de un partido: del snapshot o de la página de WhoScored.
 */
public enum LoadSource {
    SNAPSHOT,
    SCRAPING
}
