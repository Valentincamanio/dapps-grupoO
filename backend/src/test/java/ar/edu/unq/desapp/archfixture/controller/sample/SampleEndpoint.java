package ar.edu.unq.desapp.archfixture.controller.sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.archfixture.service.sample.SampleService;

// Rompe B4.1 a propósito: es un @RestController sin el sufijo Controller.
@RestController
public class SampleEndpoint {

    private final SampleService sampleService;

    public SampleEndpoint(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @GetMapping("/samples/{id}/name")
    public String nameOf(@PathVariable Long id) {
        return sampleService.getItem(id).name();
    }
}
