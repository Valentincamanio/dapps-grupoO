package ar.edu.unq.desapp.futbolmarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Propiedades del mercado de tokens (research D8 de la 008).
 *
 * <p>{@code initial-orders.automatic} decide si las órdenes iniciales se generan solas al arrancar
 * y al sincronizar el catálogo. El perfil test la apaga, como la sincronización y el recálculo del
 * arranque: hay IT que borran jugadores después de sincronizar, y una orden inicial que apunte a
 * ellos haría fallar el borrado por la clave foránea. Apagada, el servicio se puede llamar
 * directo.</p>
 */
@ConfigurationProperties("futbolmarket.market")
@Validated
public record MarketProperties(

        @NotNull
        InitialOrders initialOrders) {

    public record InitialOrders(boolean automatic) {
    }
}
