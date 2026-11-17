package ar.edu.unq.desapp.archfixture.service.sample;

import ar.edu.unq.desapp.archfixture.controller.sample.dto.SampleResponse;

// Rompe B4.2 a propósito: el servicio usa un Response del controller.
public class DtoAwareService {

    public String nameOf(SampleResponse response) {
        return response.name();
    }
}
