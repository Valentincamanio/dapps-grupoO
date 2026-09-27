package ar.edu.unq.desapp.futbolmarket.auth.modelo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyTest {

    private static final int VALUE_LENGTH = 43;
    private static final int HASH_LENGTH = 64;
    private static final String BASE64_URL_PATTERN = "[A-Za-z0-9_-]+";
    private static final String LOWERCASE_HEX_PATTERN = "[0-9a-f]+";

    @Test
    void laClaveGeneradaTiene43CaracteresBase64Url() {
        ApiKey apiKey = ApiKey.generate();

        assertThat(apiKey.value())
                .hasSize(VALUE_LENGTH)
                .matches(BASE64_URL_PATTERN);
    }

    @Test
    void dosClavesGeneradasTienenValoresDistintos() {
        assertThat(ApiKey.generate().value()).isNotEqualTo(ApiKey.generate().value());
    }

    @Test
    void elHashEsDeterministico() {
        String value = ApiKey.generate().value();

        assertThat(ApiKey.hashOf(value)).isEqualTo(ApiKey.hashOf(value));
    }

    @Test
    void elHashTiene64CaracteresHexadecimalesEnMinusculas() {
        assertThat(ApiKey.generate().hash())
                .hasSize(HASH_LENGTH)
                .matches(LOWERCASE_HEX_PATTERN);
    }

    @Test
    void elHashDeLaClaveCoincideConElHashDeSuValor() {
        ApiKey apiKey = ApiKey.generate();

        assertThat(apiKey.hash()).isEqualTo(ApiKey.hashOf(apiKey.value()));
    }

    @Test
    void elToStringNoMuestraElValor() {
        ApiKey apiKey = ApiKey.generate();

        assertThat(apiKey.toString()).doesNotContain(apiKey.value());
    }
}
