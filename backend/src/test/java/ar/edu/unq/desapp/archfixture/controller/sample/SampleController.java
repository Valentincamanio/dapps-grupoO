package ar.edu.unq.desapp.archfixture.controller.sample;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.archfixture.controller.sample.dto.SampleResponse;
import ar.edu.unq.desapp.archfixture.modelo.sample.SampleItem;
import ar.edu.unq.desapp.archfixture.service.sample.SampleService;

// De apoyo: un controller que usa el servicio y devuelve un Response.
@RestController
public class SampleController {

    private final SampleService sampleService;

    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @GetMapping("/samples/{id}")
    public SampleResponse getSample(@PathVariable Long id) {
        SampleItem item = sampleService.getItem(id);
        return new SampleResponse(item.id(), item.name(), List.of(new SampleResponse.Line(item.name())));
    }
}
