package ar.edu.unq.desapp.futbolmarket.modelo.market;

import java.math.BigDecimal;

/**
 * Una foto de una parte de un hecho del mercado: su saldo libre, su saldo reservado y sus tokens
 * del jugador (para el superusuario, el pendiente de su orden inicial).
 */
public record PartyState(long userId, BigDecimal freeBalance, BigDecimal reservedBalance, int tokens) {
}
