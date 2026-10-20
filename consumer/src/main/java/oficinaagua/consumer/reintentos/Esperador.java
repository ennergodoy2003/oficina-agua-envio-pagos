package oficinaagua.consumer.reintentos;

/** Espera entre intentos. En las pruebas se reemplaza por uno que no espera. */
@FunctionalInterface
public interface Esperador {

    /** @return false si lo interrumpieron (por ejemplo, la app se esta apagando) */
    boolean esperar(long ms);
}