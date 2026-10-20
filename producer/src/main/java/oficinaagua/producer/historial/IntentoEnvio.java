package oficinaagua.producer.historial;

import com.fasterxml.jackson.annotation.JsonInclude;
import oficinaagua.producer.pago.Destino;

/**
 * Un intento de envio, tal como lo define el contrato (schema IntentoEnvio en producer-api.yaml).
 * motivoFallo solo aparece cuando resultado es FALLO: los null no se escriben en el JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentoEnvio(
        String pagoId,
        Destino destino,
        String resultado,
        String fecha,
        String motivoFallo) {

    public static final String EXITO = "EXITO";
    public static final String FALLO = "FALLO";
}