package oficinaagua.consumer.banco;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.Set;
import java.util.UUID;
import oficinaagua.consumer.comun.FeignErrores;
import oficinaagua.consumer.pago.MensajePago;
import org.springframework.stereotype.Service;

/** Llama al banco y clasifica la respuesta. Sin estado mutable: es un Singleton. */
@Service
public class EnvioBancoService {

    private static final Set<Integer> REINTENTABLES = Set.of(429, 500, 502, 503, 504);

    private final BancoClient banco;
    private final BancoMapper mapper;
    private final ObjectMapper objectMapper;

    public EnvioBancoService(BancoClient banco, BancoMapper mapper, ObjectMapper objectMapper) {
        this.banco = banco;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public ResultadoEnvio enviar(MensajePago mensaje) {
        // Un solo numero de seguimiento para la llamada al banco y para el registro (HU-46)
        String correlationId = correlationIdDe(mensaje);

        String llave;
        BankPaymentRequest solicitud;
        try {
            llave = mapper.llaveIdempotencia(mensaje);
            solicitud = mapper.aSolicitud(mensaje);
        } catch (IllegalArgumentException e) {
            return ResultadoEnvio.fallo(mensaje.numeroDocumento(), null, "MENSAJE_INVALIDO",
                    e.getMessage(), false, correlationId);
        }

        try {
            BankPaymentReceipt recibo = banco.enviarPago(llave, correlationId, solicitud);
            return ResultadoEnvio.exito(llave, 201, recibo.receiptId(), "Entregado al banco", correlationId);
        } catch (FeignException e) {
            return desdeError(llave, correlationId, e);
        }
    }

    /** Si el producer no mando numero de seguimiento, el consumer le pone uno nuevo. */
    private String correlationIdDe(MensajePago mensaje) {
        String id = mensaje.correlationId();
        return (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id.trim();
    }

    private ResultadoEnvio desdeError(String llave, String correlationId, FeignException e) {
        int status = e.status();
        BankError error = leerError(e);
        String codigo = error != null ? error.code() : null;

        // Duplicado: el banco ya tiene este pago, se trata como entregado
        if (status == 409 && "DUPLICATE_PAYMENT".equals(codigo)) {
            String recibo = error.originalReceipt() != null ? error.originalReceipt().receiptId() : null;
            return ResultadoEnvio.exito(llave, 409, recibo,
                    "El banco ya tenia este pago: se trata como entregado", correlationId);
        }

        // status <= 0: no hubo respuesta (timeout o conexion)
        boolean sinRespuesta = status <= 0;

        // HU-60: con timeout o 504 no sabemos si el banco lo registro: se pregunta antes de reintentar
        if (sinRespuesta || status == 504) {
            ResultadoEnvio reconciliado = reconciliar(llave, correlationId);
            if (reconciliado != null) {
                return reconciliado;
            }
        }

        boolean reintentable = sinRespuesta || REINTENTABLES.contains(status);
        String detalle = sinRespuesta
                ? "Sin respuesta del banco (timeout o conexion)"
                : "El banco respondio " + status + (codigo != null ? " " + codigo : "");
        return ResultadoEnvio.fallo(llave, sinRespuesta ? null : status, codigo, detalle,
                reintentable, correlationId, FeignErrores.retryAfterSegundos(e));
    }

    /**
     * HU-60: GET del pago. 200 = ya estaba registrado: exito con su comprobante (httpStatus 200).
     * 404 = no llego; otro error = no se sabe. En esos dos casos devuelve null y se sigue reintentando.
     */
    private ResultadoEnvio reconciliar(String llave, String correlationId) {
        try {
            BankPaymentReceipt recibo = banco.consultarPago(llave, correlationId);
            return ResultadoEnvio.exito(llave, 200, recibo.receiptId(),
                    "Sin respuesta al enviar, pero el banco ya tenia el pago (reconciliado con GET)",
                    correlationId);
        } catch (FeignException e) {
            return null;
        }
    }

    private BankError leerError(FeignException e) {
        String cuerpo = FeignErrores.cuerpo(e);
        if (cuerpo == null || cuerpo.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(cuerpo, BankError.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}