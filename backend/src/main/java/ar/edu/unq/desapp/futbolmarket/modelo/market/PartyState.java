package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;

import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;

/**
 * Una foto de una parte de un hecho del mercado: su saldo libre, su saldo reservado y sus tokens
 * del jugador (para el superusuario, el pendiente de su orden inicial).
 */
public record PartyState(long userId, BigDecimal freeBalance, BigDecimal reservedBalance, int tokens) {

    /**
     * La foto de la cuenta en este momento, con sus tokens del jugador.
     */
    public static PartyState of(AppUser account, int tokens) {
        return new PartyState(account.getId(), account.availableBalance(), account.getReserved(), tokens);
    }
}
