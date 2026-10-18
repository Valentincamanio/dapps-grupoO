package ar.edu.unq.desapp.futbolmarket.adapter.whoscored;

/**
 * Baja una página de WhoScored. Es lo único que cambia si se pasa a un navegador sin ventana como
 * Playwright: los extractores, el ritmo y el reintento quedan igual (research D5).
 */
public interface PageDownloader {
    /**
     * Nunca lanza por un status de error ni por una falla de red: los informa en la página.
     */
    DownloadedPage download(String path);
}
