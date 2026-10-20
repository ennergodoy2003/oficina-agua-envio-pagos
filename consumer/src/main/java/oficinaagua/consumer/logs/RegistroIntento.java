package oficinaagua.consumer.logs;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import oficinaagua.consumer.pago.Destino;

/**
 * Una linea del libro de registro (HU-46): un intento de envio de un pago a un destino.
 * Llave de la tabla: numeroDocumento (particion) + claveIntento (orden).
 */
public record RegistroIntento(
        String numeroDocumento,
        String claveIntento,
        Destino destino,
        String resultado,
        String fecha,
        Integer httpStatus,
        String codigoError,
        String detalle,
        String correlationId,
        String receiptId,
        String numeroComprobante,
        String numeroPartida) {

    public static final String EXITOSO = "EXITOSO";
    public static final String FALLIDO = "FALLIDO";

    /** UTC con milisegundos fijos: DynamoDB ordena como texto y asi queda cronologico. */
    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /** Crea la linea de un intento nuevo: pone la fecha de ahora y arma la claveIntento. */
    public static RegistroIntento nuevo(String numeroDocumento, Destino destino, boolean exitoso,
                                        Integer httpStatus, String codigoError, String detalle,
                                        String correlationId, String receiptId,
                                        String numeroComprobante, String numeroPartida) {
        String fecha = FORMATO.format(Instant.now());
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String claveIntento = destino.name() + "#" + fecha + "#" + sufijo;
        return new RegistroIntento(numeroDocumento, claveIntento, destino,
                exitoso ? EXITOSO : FALLIDO, fecha, httpStatus, codigoError, detalle,
                correlationId, receiptId, numeroComprobante, numeroPartida);
    }
}