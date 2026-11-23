package ar.edu.unq.desapp.futbolmarket.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroupsPostProcessor;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quién ve los componentes de {@code /actuator/health} (research D3 de la 012).
 *
 * <p>Con propiedades solas no alcanza: {@code management.endpoint.health.roles} es uno solo para
 * los componentes y para los detalles, así que no se puede decir "componentes para cualquier
 * autenticado y detalles solo para {@code ADMIN}". Este post-processor envuelve el grupo primario
 * y redefine únicamente {@code showComponents}: se muestran a quien tenga principal. Los detalles,
 * los miembros, el agregador, el mapeo de status HTTP y el path adicional siguen siendo los del
 * grupo original, que es el que aplica {@code roles: ADMIN}.</p>
 *
 * <p>La raíz pública ({@code GET /actuator/health}) nunca muestra componentes: está entre las rutas
 * públicas de la cadena de seguridad, el filtro de credenciales no corre ahí y el pedido llega sin
 * principal, aunque traiga una credencial válida.</p>
 */
@Configuration
public class ActuatorHealthConfig {

    @Bean
    public HealthEndpointGroupsPostProcessor componentsForAuthenticatedCallers() {
        return ActuatorHealthConfig::withComponentsForAuthenticatedCallers;
    }

    static HealthEndpointGroups withComponentsForAuthenticatedCallers(HealthEndpointGroups groups) {
        Map<String, HealthEndpointGroup> others = new LinkedHashMap<>();
        groups.getNames().forEach(name -> others.put(name, groups.get(name)));
        return HealthEndpointGroups.of(new ComponentsForAuthenticatedGroup(groups.getPrimary()), others);
    }

    /**
     * El grupo primario con una sola diferencia: muestra los componentes a cualquier caller con
     * principal. Todo lo demás lo decide el grupo original.
     */
    record ComponentsForAuthenticatedGroup(HealthEndpointGroup original) implements HealthEndpointGroup {

        @Override
        public boolean showComponents(SecurityContext securityContext) {
            return securityContext.getPrincipal() != null;
        }

        @Override
        public boolean isMember(String name) {
            return original.isMember(name);
        }

        @Override
        public boolean showDetails(SecurityContext securityContext) {
            return original.showDetails(securityContext);
        }

        @Override
        public StatusAggregator getStatusAggregator() {
            return original.getStatusAggregator();
        }

        @Override
        public HttpCodeStatusMapper getHttpCodeStatusMapper() {
            return original.getHttpCodeStatusMapper();
        }

        @Override
        public AdditionalHealthEndpointPath getAdditionalPath() {
            return original.getAdditionalPath();
        }
    }
}
