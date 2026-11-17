package ar.edu.unq.desapp.archfixture.service.alpha;

import ar.edu.unq.desapp.archfixture.service.beta.BetaService;

// Rompe B6 a propósito: con BetaService forma un ciclo entre los contextos alpha y beta.
public class AlphaService {

    public String name() {
        return "alpha";
    }

    public String greet(BetaService beta) {
        return name() + " saluda a " + beta.name();
    }
}
