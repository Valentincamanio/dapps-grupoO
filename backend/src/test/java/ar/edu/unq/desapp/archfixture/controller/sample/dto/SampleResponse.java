package ar.edu.unq.desapp.archfixture.controller.sample.dto;

import java.util.List;

// De apoyo: un Response con un tipo anidado, que no necesita sufijo.
public record SampleResponse(Long id, String name, List<Line> lines) {

    public record Line(String text) {
    }
}
