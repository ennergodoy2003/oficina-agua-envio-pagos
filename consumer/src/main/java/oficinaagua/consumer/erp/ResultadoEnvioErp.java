package oficinaagua.consumer.erp;

/**
 * Resultado de un intento de envio al ERP. El listener lo anota en DynamoDB (HU-46).
 * El ERP devuelve dos datos de comprobante: numeroComprobante y numeroPartida.
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
        String correlationId) {

    public static ResultadoEnvioErp exito(String numeroDocumento, int httpStatus, ErpComprobante comprobante,
                                          String detalle, String correlationId) {
        String numeroComprobante = comprobante != null ? comprobante.numeroComprobante() : null;
        String numeroPartida = comprobante != null ? comprobante.numeroPartida() : null;
        return new ResultadoEnvioErp(numeroDocumento, true, httpStatus, numeroComprobante, numeroPartida,
                null, detalle, false, correlationId);
    }

    public static ResultadoEnvioErp fallo(String numeroDocumento, Integer httpStatus, String codigoError,
                                          String detalle, boolean reintentable, String correlationId) {
        return new ResultadoEnvioErp(numeroDocumento, false, httpStatus, null, null, codigoError,
                detalle, reintentable, correlationId);
    }
}
