package ar.edu.unq.desapp.archfixture.service.sample;

// Rompe B7.2 a propósito: crea una RuntimeException y un Error en vez de excepciones propias.
public class GenericExceptionService {

    public void failWithRuntimeException(String reason) {
        throw new RuntimeException(reason);
    }

    public void failWithError(String reason) {
        throw new Error(reason);
    }
}
