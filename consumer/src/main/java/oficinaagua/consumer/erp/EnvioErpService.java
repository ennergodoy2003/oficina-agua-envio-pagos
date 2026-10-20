package oficinaagua.consumer.erp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.Set;
import java.util.UUID;
import oficinaagua.consumer.comun.FeignErrores;
import oficinaagua.consumer.pago.MensajePago;
import org.springframework.stereotype.Service;

/** Llama al ERP y clasifica la respuesta. Sin estado mutable: es un Singleton. */
@Service
public class EnvioErpService {

    private static final Set<Integer> REINTENTABLES = Set.of(429, 500, 502, 503, 504);

    private final ErpClient erp;
    private final ErpMapper mapper;
    private final ObjectMapper objectMapper;

    public EnvioErpService(ErpClient erp, ErpMapper mapper, ObjectMapper objectMapper) {
        this.erp = erp;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public ResultadoEnvioErp enviar(MensajePago mensaje) {
        // Un solo numero de seguimiento para la llamada al ERP y para el registro (HU-46)
        String correlationId = correlationIdDe(mensaje);

        String numero;
        ErpRegistroPago registro;
        try {
            numero = mapper.numeroDocumento(mensaje);
            registro = mapper.aRegistro(mensaje);
        } catch (IllegalArgumentException e) {
            return ResultadoEnvioErp.fallo(mensaje.numeroDocumento(), null, "MENSAJE_INVALIDO",
                    e.getMessage(), false, correlationId);
        }

        try {
            ErpComprobante comprobante = erp.registrarPago(correlationId, registro);
            return ResultadoEnvioErp.exito(numero, 201, comprobante, "Contabilizado en el ERP", correlationId);
        } catch (FeignException e) {
            return desdeError(numero, correlationId, e);
        }
    }

    /** Si el producer no mando numero de seguimiento, el consumer le pone uno nuevo. */
    private String correlationIdDe(MensajePago mensaje) {
        String id = mensaje.correlationId();
        return (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id.trim();
    }

    private ResultadoEnvioErp desdeError(String numero, String correlationId, FeignException e) {
        int status = e.status();
        ErpError error = leerError(e);
        String codigo = error != null ? error.codigoError() : null;

        // Duplicado: el ERP ya tiene este documento con los mismos datos, se trata como entregado
        if (status == 409 && "DOCUMENTO_DUPLICADO".equals(codigo)) {
            return ResultadoEnvioErp.exito(numero, 409, error.comprobanteOriginal(),
                    "El ERP ya tenia este documento: se trata como entregado", correlationId);
        }

        // status <= 0: no hubo respuesta (timeout o conexion), puede reintentarse
        boolean sinRespuesta = status <= 0;
        boolean reintentable = sinRespuesta || REINTENTABLES.contains(status);
        String detalle = sinRespuesta
                ? "Sin respuesta del ERP (timeout o conexion)"
                : "El ERP respondio " + status + (codigo != null ? " " + codigo : "");
        return ResultadoEnvioErp.fallo(numero, sinRespuesta ? null : status, codigo, detalle,
                reintentable, correlationId, FeignErrores.retryAfterSegundos(e));
    }

    /** API Gateway a veces responde {"message":"Forbidden"}: sin formato ErpError, codigo queda null. */
    private ErpError leerError(FeignException e) {
        String cuerpo = FeignErrores.cuerpo(e);
        if (cuerpo == null || cuerpo.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(cuerpo, ErpError.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}