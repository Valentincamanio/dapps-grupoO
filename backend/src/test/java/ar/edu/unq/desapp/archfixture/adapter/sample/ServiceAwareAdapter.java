package ar.edu.unq.desapp.archfixture.adapter.sample;

import ar.edu.unq.desapp.archfixture.service.sample.SampleService;

// Rompe B5.2 a propósito: el adapter usa un servicio.
public class ServiceAwareAdapter {

    private final SampleService sampleService;

    public ServiceAwareAdapter(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    public String nameOf(Long id) {
        return sampleService.getItem(id).name();
    }
}
