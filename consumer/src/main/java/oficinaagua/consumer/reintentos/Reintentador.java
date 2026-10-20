package oficinaagua.consumer.reintentos;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HU-48: intenta, anota cada intento y reintenta con backoff los fallos reintentables.
 * Se detiene con exito, con un fallo no reintentable o al llegar al maximo de intentos.
 */
@Component
public class Reintentador {

    private static final Logger log = LoggerFactory.getLogger(Reintentador.class);

    private final PoliticaReintentos politica;
    private final Esperador esperador;

    public Reintentador(PoliticaReintentos politica, Esperador esperador) {
        this.politica = politica;
        this.esperador = esperador;
    }

    /**
     * @param intento hace un envio y devuelve su resultado
     * @param anotar se llama despues de CADA intento (HU-46: una linea por intento)
     */
    public <R extends ResultadoIntento> Desenlace<R> ejecutar(Supplier<R> intento, Consumer<R> anotar) {
        R resultado = null;
        int numero = 0;
        while (numero < politica.maxIntentos()) {
            numero++;
            resultado = intento.get();
            anotar.accept(resultado);

            if (resultado.exitoso() || !resultado.reintentable() || numero == politica.maxIntentos()) {
                break;
            }
            long espera = politica.esperaMs(numero, resultado.retryAfterSegundos());
            log.info("Intento {} de {} fallido, se reintenta en {} ms", numero, politica.maxIntentos(), espera);
            if (!esperador.esperar(espera)) {
                break; // interrumpido: no se sigue reintentando
            }
        }
        return new Desenlace<>(resultado, numero);
    }

    /** El ultimo resultado y cuantos intentos se hicieron. */
    public record Desenlace<R>(R resultado, int intentos) {
    }
}