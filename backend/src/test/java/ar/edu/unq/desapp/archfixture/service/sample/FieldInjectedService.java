package ar.edu.unq.desapp.archfixture.service.sample;

import org.springframework.beans.factory.annotation.Autowired;

// Rompe B7.4 a propósito: recibe su dependencia por un campo con @Autowired.
public class FieldInjectedService {

    @Autowired
    private SampleService sampleService;

    public String nameOf(Long id) {
        return sampleService.getItem(id).name();
    }
}
