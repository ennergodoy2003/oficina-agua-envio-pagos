package oficinaagua.consumer.banco;

/**
 * Resultado de un intento de envio al banco. El listener lo anota en DynamoDB (HU-46).
 * correlationId: el mismo numero de seguimiento que se mando al banco en X-Correlation-Id.
 */
public record ResultadoEnvio(
        String numeroDocumento,
        boolean exitoso,
        Integer httpStatus,
        String receiptId,
        String codigoError,
        String detalle,
        boolean reintentable,
        String correlationId) {

    public static ResultadoEnvio exito(String numeroDocumento, int httpStatus, String receiptId,
                                       String detalle, String correlationId) {
        return new ResultadoEnvio(numeroDocumento, true, httpStatus, receiptId, null, detalle, false,
                correlationId);
    }

    public static ResultadoEnvio fallo(String numeroDocumento, Integer httpStatus, String codigoError,
                                       String detalle, boolean reintentable, String correlationId) {
        return new ResultadoEnvio(numeroDocumento, false, httpStatus, null, codigoError, detalle,
                reintentable, correlationId);
    }
}