package ar.edu.unq.desapp.futbolmarket.auth.modelo;

/**
 * Rol de una cuenta. Se persiste como texto y en Spring Security se traduce a las autoridades
 * {@code ROLE_USER} y {@code ROLE_ADMIN}.
 */
public enum Role {

    /** Usuario común. Es el único rol que otorga el registro público. */
    USER,

    /** Administrador. Existe solo por el alta de arranque. */
    ADMIN
}
