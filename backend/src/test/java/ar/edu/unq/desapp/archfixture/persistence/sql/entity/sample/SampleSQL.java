package ar.edu.unq.desapp.archfixture.persistence.sql.entity.sample;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

// De apoyo: una entidad en persistence/sql/entity y con el sufijo SQL.
@Entity
public class SampleSQL {

    @Id
    private Long id;

    private String name;

    public SampleSQL(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
