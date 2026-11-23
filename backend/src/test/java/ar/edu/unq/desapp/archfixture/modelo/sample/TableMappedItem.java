package ar.edu.unq.desapp.archfixture.modelo.sample;

import jakarta.persistence.Table;

// Rompe B2 y B3.1 a propósito: el modelo lleva una anotación de JPA.
@Table(name = "table_mapped_items")
public record TableMappedItem(Long id, String name) {
}
