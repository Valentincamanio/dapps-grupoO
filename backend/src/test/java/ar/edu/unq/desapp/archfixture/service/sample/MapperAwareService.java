package ar.edu.unq.desapp.archfixture.service.sample;

import ar.edu.unq.desapp.archfixture.modelo.sample.SampleItem;
import ar.edu.unq.desapp.archfixture.persistence.mapper.sample.SampleMapper;
import ar.edu.unq.desapp.archfixture.persistence.sql.entity.sample.SampleSQL;

// Rompe B3.4 a propósito: el servicio usa un mapper, que solo puede usar el repository.
public class MapperAwareService {

    private final SampleMapper sampleMapper;

    public MapperAwareService(SampleMapper sampleMapper) {
        this.sampleMapper = sampleMapper;
    }

    public SampleItem toModel(SampleSQL sample) {
        return sampleMapper.toModel(sample);
    }
}
