package ar.edu.unq.desapp.archfixture.service.sample;

import ar.edu.unq.desapp.archfixture.adapter.sample.dto.SampleProviderDto;

// Rompe B5.1 a propósito: el servicio usa un DTO del proveedor.
public class ProviderDtoAwareService {

    public String labelOf(SampleProviderDto dto) {
        return dto.label();
    }
}
