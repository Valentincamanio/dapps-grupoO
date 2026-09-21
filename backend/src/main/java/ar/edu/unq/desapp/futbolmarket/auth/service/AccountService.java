package ar.edu.unq.desapp.futbolmarket.auth.service;

import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.AppUser;
import ar.edu.unq.desapp.futbolmarket.auth.modelo.exception.InvalidCredentialsException;
import ar.edu.unq.desapp.futbolmarket.auth.persistence.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Operaciones sobre la cuenta del usuario autenticado.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AppUserRepository appUserRepository;

    /**
     * El filtro ya resolvió al usuario antes de llegar acá, así que un id inexistente solo puede
     * aparecer en una carrera. Se trata como credencial inválida, igual que en el filtro.
     */
    public AppUser getProfile(Long userId) {
        return appUserRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
    }
}
