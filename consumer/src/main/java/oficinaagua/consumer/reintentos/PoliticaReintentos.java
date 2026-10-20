package oficinaagua.consumer.reintentos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HU-48: cuanto esperar entre intentos y cuantos intentos hacer.
 * Backoff exponencial con tope; si el destino manda Retry-After, ese valor manda.
 */
@Component
public class PoliticaReintentos {

    private final int maxIntentos;
    private final long esperaInicialMs;
    private final double multiplicador;
    private final long esperaMaximaMs;

    public PoliticaReintentos(@Value("${envio.reintentos.max-intentos:4}") int maxIntentos,
                              @Value("${envio.reintentos.espera-inicial-ms:1000}") long esperaInicialMs,
                              @Value("${envio.reintentos.multiplicador:2}") double multiplicador,
                              @Value("${envio.reintentos.espera-maxima-ms:30000}") long esperaMaximaMs) {
        this.maxIntentos = Math.max(1, maxIntentos);
        this.esperaInicialMs = esperaInicialMs;
        this.multiplicador = multiplicador;
        this.esperaMaximaMs = esperaMaximaMs;
    }

    public int maxIntentos() {
        return maxIntentos;
    }

    /**
     * Espera antes del siguiente intento.
     * @param intento el numero del intento que acaba de fallar (1, 2, 3...)
     * @param retryAfterSegundos lo que pidio el destino, o null si no lo mando
     */
    public long esperaMs(int intento, Long retryAfterSegundos) {
        if (retryAfterSegundos != null && retryAfterSegundos > 0) {
            return Math.min(retryAfterSegundos * 1000, esperaMaximaMs);
        }
        double espera = esperaInicialMs * Math.pow(multiplicador, intento - 1);
        return (long) Math.min(espera, esperaMaximaMs);
    }
}