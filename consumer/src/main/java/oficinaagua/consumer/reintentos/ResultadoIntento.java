package oficinaagua.consumer.reintentos;

/** Lo que el Reintentador necesita saber de un intento, sea del banco o del ERP. */
public interface ResultadoIntento {

    boolean exitoso();

    boolean reintentable();

    Long retryAfterSegundos();
}