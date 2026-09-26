package ar.edu.unq.desapp.futbolmarket.catalog.modelo;

import ar.edu.unq.desapp.futbolmarket.shared.BadRequestException;

/**
 * Invariante de dominio del catálogo: es la contraparte de InvalidUserDataException de auth, así
 * que cae en la misma categoría de 400 (principio III).
 */
public class CatalogInvariantException extends BadRequestException {
    public CatalogInvariantException(String message) {
        super(message);
    }
}
