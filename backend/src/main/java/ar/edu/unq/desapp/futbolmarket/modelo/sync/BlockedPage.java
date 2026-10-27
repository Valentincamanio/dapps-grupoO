package ar.edu.unq.desapp.futbolmarket.modelo.sync;

/**
 * Una página de WhoScored: su tipo, el id de WhoScored que la identifica y su ruta. Si WhoScored
 * la rechaza dos veces seguidas, la corrida se corta en ella (FR-043 y FR-044).
 */
public record BlockedPage(PageKind kind, String whoscoredId, String path) {
}
