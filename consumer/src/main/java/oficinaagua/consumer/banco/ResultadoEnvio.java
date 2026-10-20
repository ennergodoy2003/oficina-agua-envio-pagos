package oficinaagua.consumer.banco;
import oficinaagua.consumer.reintentos.ResultadoIntento;
/**
 * Resultado de un intento de envio al banco. El listener lo anota en DynamoDB (HU-46).
 * retryAfterSegundos: lo que pidio el banco en Retry-After (HU-48), o null.
 */
public record ResultadoEnvio(
        String numeroDocumento,
        boolean exitoso,
        Integer httpStatus,
        String receiptId,
        String codigoError,
        String detalle,
        boolean reintentable,
        String correlationId,
        Long retryAfterSegundos) implements ResultadoIntento {

    public static ResultadoEnvio exito(String numeroDocumento, int httpStatus, String receiptId,
                                       String detalle, String correlationId) {
        return new ResultadoEnvio(numeroDocumento, true, httpStatus, receiptId, null, detalle, false,
                correlationId, null);
    }

    public static ResultadoEnvio fallo(String numeroDocumento, Integer httpStatus, String codigoError,
                                       String detalle, boolean reintentable, String correlationId) {
        return fallo(numeroDocumento, httpStatus, codigoError, detalle, reintentable, correlationId, null);
    }

    public static ResultadoEnvio fallo(String numeroDocumento, Integer httpStatus, String codigoError,
                                       String detalle, boolean reintentable, String correlationId,
                                       Long retryAfterSegundos) {
        return new ResultadoEnvio(numeroDocumento, false, httpStatus, null, codigoError, detalle,
                reintentable, correlationId, retryAfterSegundos);
    }
}