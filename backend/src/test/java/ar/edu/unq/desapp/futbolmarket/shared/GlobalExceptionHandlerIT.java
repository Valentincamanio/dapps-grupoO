package ar.edu.unq.desapp.futbolmarket.shared;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerIT {
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ErrorProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void devuelveApiErrorParaSolicitudInvalida() throws Exception {
        mockMvc.perform(get("/error-probe/bad").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Los parámetros enviados no son válidos."))
                .andExpect(jsonPath("$.path").value("/error-probe/bad"));
    }

    @Test
    void devuelveApiErrorParaJugadorInexistente() throws Exception {
        mockMvc.perform(get("/error-probe/missing").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No se encontró el jugador con id 99."))
                .andExpect(jsonPath("$.path").value("/error-probe/missing"));
    }

    @RestController
    @RequestMapping("/error-probe")
    static class ErrorProbeController {
        @GetMapping("/bad")
        void badRequest() {
            throw new IllegalArgumentException("entrada inválida");
        }

        @GetMapping("/missing")
        void missingPlayer() {
            throw new PlayerNotFoundException(99L);
        }
    }
}
