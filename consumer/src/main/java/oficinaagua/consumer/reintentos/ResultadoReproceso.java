package oficinaagua.consumer.reintentos;

/** Respuesta del endpoint de reproceso de la DLQ. */
public record ResultadoReproceso(int reprocesados, long quedanEnDlq) {
}