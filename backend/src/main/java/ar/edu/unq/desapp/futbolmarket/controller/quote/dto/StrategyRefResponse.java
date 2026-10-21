package ar.edu.unq.desapp.futbolmarket.controller.quote.dto;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyVersionRef;

/**
 * La estrategia y la versión, con los campos del esquema {@code StrategyRef} del contrato:
 * {@code id} es el de la estrategia (el de su versión 1), no el de la versión.
 */
public record StrategyRefResponse(Long id, String name, int version) {

    public static StrategyRefResponse from(StrategyVersionRef ref) {
        return new StrategyRefResponse(ref.strategyId(), ref.name(), ref.version());
    }
}
