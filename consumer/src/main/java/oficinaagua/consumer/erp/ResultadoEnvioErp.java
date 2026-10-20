package oficinaagua.consumer.erp;

import oficinaagua.consumer.reintentos.ResultadoIntento;

/**
 * Resultado de un intento de envio al ERP. El listener lo anota en DynamoDB (HU-46).
 * retryAfterSegundos: lo que pidio el ERP en Retry-After (HU-48), o null.
 */
public record ResultadoEnvioErp(
        String numeroDocumento,
        boolean exitoso,
        Integer httpStatus,
        String numeroComprobante,
        String numeroPartida,
        String codigoError,
        String detalle,
        boolean reintentable,
        String correlationId,
        Long retryAfterSegundos) implements ResultadoIntento {

    public static ResultadoEnvioErp exito(String numeroDocumento, int httpStatus, ErpComprobante comprobante,
                                          String detalle, String correlationId) {
        String numeroComprobante = comprobante != null ? comprobante.numeroComprobante() : null;
        String numeroPartida = comprobante != null ? comprobante.numeroPartida() : null;
        return new ResultadoEnvioErp(numeroDocumento, true, httpStatus, numeroComprobante, numeroPartida,
                null, detalle, false, correlationId, null);
    }

    public static ResultadoEnvioErp fallo(String numeroDocumento, Integer httpStatus, String codigoError,
                                          String detalle, boolean reintentable, String correlationId) {
        return fallo(numeroDocumento, httpStatus, codigoError, detalle, reintentable, correlationId, null);
    }

    public static ResultadoEnvioErp fallo(String numeroDocumento, Integer httpStatus, String codigoError,
                                          String detalle, boolean reintentable, String correlationId,
                                          Long retryAfterSegundos) {
        return new ResultadoEnvioErp(numeroDocumento, false, httpStatus, null, null, codigoError,
                detalle, reintentable, correlationId, retryAfterSegundos);
    }
}