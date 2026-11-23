package ar.edu.unq.desapp.archfixture.service.beta;

import ar.edu.unq.desapp.archfixture.service.alpha.AlphaService;

// Rompe B6 a propósito: con AlphaService forma un ciclo entre los contextos alpha y beta.
public class BetaService {

    public String name() {
        return "beta";
    }

    public String greet(AlphaService alpha) {
        return name() + " saluda a " + alpha.name();
    }
}
