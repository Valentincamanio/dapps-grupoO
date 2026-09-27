package ar.edu.unq.desapp.futbolmarket.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.auth.modelo.PasswordHasher;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador del puerto {@link PasswordHasher}: delega en el {@code PasswordEncoder} BCrypt. Es lo
 * que mantiene a Spring Security fuera del modelo.
 */
@Component
@RequiredArgsConstructor
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String plainText) {
        return passwordEncoder.encode(plainText);
    }

    @Override
    public boolean matches(String plainText, String hash) {
        return passwordEncoder.matches(plainText, hash);
    }
}
