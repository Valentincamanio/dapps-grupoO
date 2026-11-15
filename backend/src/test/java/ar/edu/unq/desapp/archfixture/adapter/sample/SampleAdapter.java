package ar.edu.unq.desapp.archfixture.adapter.sample;

import ar.edu.unq.desapp.archfixture.adapter.sample.dto.SampleProviderDto;
import ar.edu.unq.desapp.archfixture.modelo.sample.SampleItem;

// De apoyo: traduce el DTO del proveedor al modelo.
public class SampleAdapter {

    public SampleItem toModel(SampleProviderDto dto) {
        return new SampleItem(dto.id(), dto.label());
    }
}
