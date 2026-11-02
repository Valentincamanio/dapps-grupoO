package ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market;

import java.math.BigDecimal;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Una parte de un registro de auditoría: el usuario y su saldo libre, su saldo reservado y sus
 * tokens del jugador, antes y después del hecho (FR-050). Los nombres de las columnas los pone
 * {@link AuditEntrySQL}, que la usa dos veces. Sin setters, como el registro.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AuditPartySQL {
    private Long userId;
    private BigDecimal freeBefore;
    private BigDecimal freeAfter;
    private BigDecimal reservedBefore;
    private BigDecimal reservedAfter;
    private Integer tokensBefore;
    private Integer tokensAfter;
}
