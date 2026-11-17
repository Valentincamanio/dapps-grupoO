package ar.edu.unq.desapp.archfixture.service.sample;

import java.util.logging.Logger;

// Rompe B7.3 a propósito: usa java.util.logging en vez de SLF4J.
public class JavaUtilLoggingService {

    private static final Logger LOGGER = Logger.getLogger(JavaUtilLoggingService.class.getName());

    public void log(String message) {
        LOGGER.info(message);
    }
}
