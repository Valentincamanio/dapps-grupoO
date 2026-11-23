package ar.edu.unq.desapp.archfixture.service.sample;

import ar.edu.unq.desapp.archfixture.modelo.sample.SampleItem;
import ar.edu.unq.desapp.archfixture.persistence.repository.sample.SampleRepository;

// De apoyo: usa el repository y devuelve modelo, sin conocer las entidades.
public class SampleService {

    private final SampleRepository sampleRepository;

    public SampleService(SampleRepository sampleRepository) {
        this.sampleRepository = sampleRepository;
    }

    public SampleItem getItem(Long id) {
        return sampleRepository.findById(id).orElseThrow();
    }
}
