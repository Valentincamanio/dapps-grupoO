package ar.edu.unq.desapp.archfixture.persistence.mapper.sample;

import ar.edu.unq.desapp.archfixture.modelo.sample.SampleItem;
import ar.edu.unq.desapp.archfixture.persistence.sql.entity.sample.SampleSQL;

// De apoyo: traduce la entidad al modelo.
public class SampleMapper {

    public SampleItem toModel(SampleSQL sample) {
        return new SampleItem(sample.getId(), sample.getName());
    }
}
