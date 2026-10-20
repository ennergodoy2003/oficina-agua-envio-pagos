package oficinaagua.consumer.reintentos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PoliticaReintentosTest {

    private final PoliticaReintentos politica = new PoliticaReintentos(4, 1000, 2, 30000);

    @Test
    void sinRetryAfter_laEsperaSeDuplicaCadaIntento() {
        assertThat(politica.esperaMs(1, null)).isEqualTo(1000);
        assertThat(politica.esperaMs(2, null)).isEqualTo(2000);
        assertThat(politica.esperaMs(3, null)).isEqualTo(4000);
        assertThat(politica.esperaMs(4, null)).isEqualTo(8000);
    }

    @Test
    void laEsperaNuncaPasaDelTope() {
        assertThat(politica.esperaMs(10, null)).isEqualTo(30000);
    }

    @Test
    void siVieneRetryAfter_eseValorManda() {
        assertThat(politica.esperaMs(1, 5L)).isEqualTo(5000);
    }

    @Test
    void retryAfterExagerado_tambienRespetaElTope() {
        assertThat(politica.esperaMs(1, 3600L)).isEqualTo(30000);
    }
}