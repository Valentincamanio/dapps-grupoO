package ar.edu.unq.desapp.archfixture.controller.sample;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.unq.desapp.archfixture.modelo.sample.SampleItem;
import ar.edu.unq.desapp.archfixture.persistence.sql.entity.sample.SampleSQL;
import ar.edu.unq.desapp.archfixture.service.sample.SampleService;

// Rompe B4.3 a propósito: sus endpoints devuelven modelo o una entidad, directos, en un genérico y en un arreglo.
@RestController
public class ModelLeakController {

    private static final Long FIRST_ID = 1L;

    private final SampleService sampleService;

    public ModelLeakController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @GetMapping("/leaks/item")
    public SampleItem item() {
        return sampleService.getItem(FIRST_ID);
    }

    @GetMapping("/leaks/items")
    public ResponseEntity<List<SampleItem>> items() {
        return ResponseEntity.ok(List.of(item()));
    }

    @GetMapping("/leaks/item-array")
    public SampleItem[] itemArray() {
        return new SampleItem[] {item()};
    }

    @GetMapping("/leaks/entity")
    public Optional<SampleSQL> entity() {
        SampleItem item = item();
        return Optional.of(new SampleSQL(item.id(), item.name()));
    }
}
