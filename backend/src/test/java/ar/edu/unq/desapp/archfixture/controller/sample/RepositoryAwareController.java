package ar.edu.unq.desapp.archfixture.controller.sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.archfixture.persistence.repository.sample.SampleRepository;

// Rompe B1 a propósito: el controller usa un repository y se saltea el servicio.
@RestController
public class RepositoryAwareController {

    private final SampleRepository sampleRepository;

    public RepositoryAwareController(SampleRepository sampleRepository) {
        this.sampleRepository = sampleRepository;
    }

    @GetMapping("/samples/{id}/exists")
    public boolean exists(@PathVariable Long id) {
        return sampleRepository.findById(id).isPresent();
    }
}
