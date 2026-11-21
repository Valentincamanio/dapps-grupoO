package ar.edu.unq.desapp.futbolmarket.service.user;

import org.springframework.stereotype.Component;

import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;

/**
 * El gauge de cuentas registradas, incluida la del administrador (research D7 de la 012, FR-015).
 * Se evalúa en cada lectura, así refleja la base en ese momento. Vive en el servicio porque solo el
 * servicio puede usar la persistencia.
 */
@Component
@RequiredArgsConstructor
public class RegisteredUsersGauge implements MeterBinder {
    public static final String REGISTERED_USERS = "futbolmarket.users.registered";

    private static final String DESCRIPTION = "Cantidad actual de cuentas registradas, incluida la del administrador.";

    private final AppUserRepository appUserRepository;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(REGISTERED_USERS, appUserRepository, AppUserRepository::count)
                .description(DESCRIPTION)
                .strongReference(true)
                .register(registry);
    }
}
