package ar.edu.unq.desapp.futbolmarket.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;

/**
 * El grupo primario envuelto por {@link ActuatorHealthConfig}: cambia solo quién ve los componentes
 * y delega todo lo demás en el grupo original (research D3 de la 012).
 */
class ActuatorHealthConfigTest {

    private static final String MEMBER = "db";
    private static final String OTHER_GROUP = "custom";

    private HealthEndpointGroup original;
    private HealthEndpointGroup otherGroup;
    private HealthEndpointGroups wrapped;

    @BeforeEach
    void setUp() {
        original = mock(HealthEndpointGroup.class);
        otherGroup = mock(HealthEndpointGroup.class);
        HealthEndpointGroups groups = HealthEndpointGroups.of(original, Map.of(OTHER_GROUP, otherGroup));

        wrapped = new ActuatorHealthConfig().componentsForAuthenticatedCallers().postProcessHealthEndpointGroups(groups);
    }

    @Test
    void conPrincipalSeMuestranLosComponentes() {
        SecurityContext authenticated = contextWith(() -> "user");

        assertThat(wrapped.getPrimary().showComponents(authenticated)).isTrue();
    }

    @Test
    void sinPrincipalNoSeMuestranLosComponentesAunqueElOriginalLosMostrara() {
        SecurityContext anonymous = contextWith(null);
        when(original.showComponents(anonymous)).thenReturn(true);

        assertThat(wrapped.getPrimary().showComponents(anonymous)).isFalse();
    }

    @Test
    void losDetallesLosDecideElGrupoOriginal() {
        SecurityContext admin = contextWith(() -> "admin");
        SecurityContext user = contextWith(() -> "user");
        when(original.showDetails(admin)).thenReturn(true);
        when(original.showDetails(user)).thenReturn(false);

        assertThat(wrapped.getPrimary().showDetails(admin)).isTrue();
        assertThat(wrapped.getPrimary().showDetails(user)).isFalse();
    }

    @Test
    void losMiembrosElAgregadorElMapperYElPathSonLosDelOriginal() {
        StatusAggregator aggregator = mock(StatusAggregator.class);
        HttpCodeStatusMapper mapper = mock(HttpCodeStatusMapper.class);
        AdditionalHealthEndpointPath additionalPath = AdditionalHealthEndpointPath.from("server:/healthz");
        when(original.isMember(MEMBER)).thenReturn(true);
        when(original.getStatusAggregator()).thenReturn(aggregator);
        when(original.getHttpCodeStatusMapper()).thenReturn(mapper);
        when(original.getAdditionalPath()).thenReturn(additionalPath);

        HealthEndpointGroup primary = wrapped.getPrimary();

        assertThat(primary.isMember(MEMBER)).isTrue();
        assertThat(primary.isMember("otro")).isFalse();
        assertThat(primary.getStatusAggregator()).isSameAs(aggregator);
        assertThat(primary.getHttpCodeStatusMapper()).isSameAs(mapper);
        assertThat(primary.getAdditionalPath()).isSameAs(additionalPath);
    }

    @Test
    void losDemasGruposQuedanComoEstaban() {
        assertThat(wrapped.getNames()).containsExactly(OTHER_GROUP);
        assertThat(wrapped.get(OTHER_GROUP)).isSameAs(otherGroup);
    }

    private static SecurityContext contextWith(Principal principal) {
        SecurityContext context = mock(SecurityContext.class);
        when(context.getPrincipal()).thenReturn(principal);
        return context;
    }
}
