package ar.edu.unq.desapp.futbolmarket.controller.market.dto;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyState;

/**
 * Una parte de un registro de auditoría (esquema {@code AuditParty}): su papel en el hecho y su saldo
 * libre, su saldo reservado y sus tokens del jugador antes y después (FR-050).
 */
public record AuditPartyResponse(Role role, long userId, State before, State after) {

    /**
     * {@code OWNER} en una orden creada o cancelada; {@code BUYER} y {@code SELLER} en una operación.
     */
    public enum Role { OWNER, BUYER, SELLER }

    /**
     * Una foto de la parte (esquema {@code PartyState}). Para el superusuario, los tokens son el
     * pendiente de su orden inicial.
     */
    public record State(BigDecimal availableBalance, BigDecimal reservedBalance, int tokens) {

        static State from(PartyState state) {
            return new State(state.freeBalance(), state.reservedBalance(), state.tokens());
        }
    }

    public static AuditPartyResponse from(Role role, PartyChange change) {
        return new AuditPartyResponse(role, change.userId(), State.from(change.before()), State.from(change.after()));
    }
}
