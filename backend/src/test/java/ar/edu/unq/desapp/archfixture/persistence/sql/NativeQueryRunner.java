package ar.edu.unq.desapp.archfixture.persistence.sql;

import jakarta.persistence.EntityManager;

// Rompe B3.5 a propósito: arma una query nativa en tiempo de ejecución.
public class NativeQueryRunner {

    private final EntityManager entityManager;

    public NativeQueryRunner(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Object countSamples() {
        return entityManager.createNativeQuery("SELECT COUNT(*) FROM samples").getSingleResult();
    }
}
