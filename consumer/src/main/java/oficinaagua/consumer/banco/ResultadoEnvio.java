package oficinaagua.consumer.banco;

/** Resultado de un intento de envio al banco. HU-46 lo registrara en DynamoDB. */
public record ResultadoEnvio(
        String numeroDocumento,
        boolean exitoso,
        Integer httpStatus,
        String receiptId,
        String codigoError,
        String detalle,
        boolean reintentable) {

    public static ResultadoEnvio exito(String numeroDocumento, int httpStatus, String receiptId, String detalle) {
        return new ResultadoEnvio(numeroDocumento, true, httpStatus, receiptId, null, detalle, false);
    }

    public static ResultadoEnvio fallo(String numeroDocumento, Integer httpStatus, String codigoError,
                                       String detalle, boolean reintentable) {
        return new ResultadoEnvio(numeroDocumento, false, httpStatus, null, codigoError, detalle, reintentable);
    }
}