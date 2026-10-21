package oficinaagua.consumer.reintentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El ciclo de reintentos, sin esperar de verdad: el Esperador solo anota cuanto habria esperado. */
class ReintentadorTest {

    record Falso(boolean exitoso, boolean reintentable, Long retryAfterSegundos) implements ResultadoIntento {
    }

    private static final Falso EXITO = new Falso(true, false, null);
    private static final Falso CAIDO = new Falso(false, true, null);
    private static final Falso RECHAZO = new Falso(false, false, null);

    private final List<Long> esperas = new ArrayList<>();
    private final List<Falso> anotados = new ArrayList<>();
    private final Reintentador reintentador = new Reintentador(
            new PoliticaReintentos(4, 1000, 2, 30000), ms -> {
        esperas.add(ms);
        return true;
    });

    @Test
    void exitoAlPrimerIntento_noEspera() {
        Reintentador.Desenlace<Falso> d = ejecutar(EXITO);

        assertThat(d.intentos()).isEqualTo(1);
        assertThat(d.resultado().exitoso()).isTrue();
        assertThat(esperas).isEmpty();
    }

    @Test
    void caidoDosVeces_luegoExito_conBackoffExponencial() {
        Reintentador.Desenlace<Falso> d = ejecutar(CAIDO, CAIDO, EXITO);

        assertThat(d.intentos()).isEqualTo(3);
        assertThat(d.resultado().exitoso()).isTrue();
        assertThat(esperas).containsExactly(1000L, 2000L);
        assertThat(anotados).hasSize(3); // una linea por intento
    }

    @Test
    void rechazoNoReintentable_unSoloIntento() {
        Reintentador.Desenlace<Falso> d = ejecutar(RECHAZO, EXITO);

        assertThat(d.intentos()).isEqualTo(1);
        assertThat(d.resultado().exitoso()).isFalse();
        assertThat(esperas).isEmpty();
    }

    @Test
    void siempreCaido_seRindeAlMaximoDeIntentos() {
        Reintentador.Desenlace<Falso> d = ejecutar(CAIDO, CAIDO, CAIDO, CAIDO, CAIDO);

        assertThat(d.intentos()).isEqualTo(4);
        assertThat(d.resultado().exitoso()).isFalse();
        assertThat(esperas).containsExactly(1000L, 2000L, 4000L);
        assertThat(anotados).hasSize(4);
    }

    @Test
    void retryAfter_mandaSobreElBackoff() {
        ejecutar(new Falso(false, true, 5L), EXITO);

        assertThat(esperas).containsExactly(5000L);
    }

    @Test
    void siLoInterrumpen_dejaDeReintentar() {
        Reintentador interrumpido = new Reintentador(new PoliticaReintentos(4, 1000, 2, 30000), ms -> false);
        Iterator<Falso> respuestas = List.of(CAIDO, EXITO).iterator();

        Reintentador.Desenlace<Falso> d = interrumpido.ejecutar(respuestas::next, r -> { });

        assertThat(d.intentos()).isEqualTo(1);
        assertThat(d.resultado().exitoso()).isFalse();
    }

    /** Cada intento devuelve la siguiente respuesta de la lista. */
    private Reintentador.Desenlace<Falso> ejecutar(Falso... respuestas) {
        Iterator<Falso> it = List.of(respuestas).iterator();
        return reintentador.ejecutar(it::next, anotados::add);
    }
}