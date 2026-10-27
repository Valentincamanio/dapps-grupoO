package ar.edu.unq.desapp.futbolmarket.modelo.quote;

import java.math.BigDecimal;

/**
 * Cuánto cambió una cotización respecto de su valor base: en créditos y en porcentaje, los dos con
 * dos decimales. Se informa y no se guarda.
 */
public record QuoteVariation(BigDecimal amount, BigDecimal percent) {
}
