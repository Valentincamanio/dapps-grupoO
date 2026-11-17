package ar.edu.unq.desapp.archfixture.persistence.sql.entity.sample;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

// Rompe B3.2 a propósito: es una entidad sin el sufijo SQL.
@Entity
public class SampleRecord {

    @Id
    private Long id;

    public SampleRecord(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
