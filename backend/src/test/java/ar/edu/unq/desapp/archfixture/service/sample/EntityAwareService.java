package ar.edu.unq.desapp.archfixture.service.sample;

import ar.edu.unq.desapp.archfixture.persistence.sql.entity.sample.SampleSQL;

// Rompe B3.6 a propósito: el servicio recibe una entidad.
public class EntityAwareService {

    public String nameOf(SampleSQL sample) {
        return sample.getName();
    }
}
