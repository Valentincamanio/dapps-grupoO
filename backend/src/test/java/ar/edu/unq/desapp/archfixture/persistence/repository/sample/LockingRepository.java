package ar.edu.unq.desapp.archfixture.persistence.repository.sample;

import jakarta.persistence.LockModeType;

// Rompe B3.1 a propósito: usa la API de JPA fuera de persistence/sql.
public class LockingRepository {

    public LockModeType lockModeFor(boolean exclusive) {
        return exclusive ? LockModeType.PESSIMISTIC_WRITE : LockModeType.PESSIMISTIC_READ;
    }
}
