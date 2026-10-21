package ar.edu.unq.desapp.futbolmarket.controller.strategy.dto;

import java.util.List;

import ar.edu.unq.desapp.futbolmarket.modelo.strategy.Strategy;
import ar.edu.unq.desapp.futbolmarket.modelo.strategy.StrategyDetail;

/**
 * Una estrategia con todas sus versiones, de la 1 en adelante (esquema {@code StrategyResponse} del
 * contrato). {@code id} es el de su versión 1. Si la estrategia es la activa, su versión activa es
 * la última; si no, ninguna lo es.
 */
public record StrategyResponse(Long id, String name, boolean active, List<StrategyVersionResponse> versions) {

    public static StrategyResponse from(StrategyDetail detail) {
        Strategy strategy = detail.strategy();
        int latest = strategy.latest().version();
        List<StrategyVersionResponse> versions = strategy.versions().stream()
                .map(version -> StrategyVersionResponse.from(version, detail.active() && version.version() == latest))
                .toList();
        return new StrategyResponse(strategy.id(), strategy.name(), detail.active(), versions);
    }
}
