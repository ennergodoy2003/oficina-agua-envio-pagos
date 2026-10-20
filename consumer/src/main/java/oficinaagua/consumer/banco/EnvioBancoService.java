package oficinaagua.consumer.banco;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.Set;
import java.util.UUID;
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
        String llave;
        BankPaymentRequest solicitud;
        try {
            llave = mapper.llaveIdempotencia(mensaje);
            solicitud = mapper.aSolicitud(mensaje);
        } catch (IllegalArgumentException e) {
            return ResultadoEnvio.fallo(mensaje.numeroDocumento(), null, "MENSAJE_INVALIDO", e.getMessage(), false);
        }

        try {
            BankPaymentReceipt recibo = banco.enviarPago(llave, correlationIdDe(mensaje), solicitud);
            return ResultadoEnvio.exito(llave, 201, recibo.receiptId(), "Entregado al banco");
        } catch (FeignException e) {
            return desdeError(llave, e);
        }
    }

    /** Si el producer no mando numero de seguimiento, el consumer le pone uno nuevo. */
    private String correlationIdDe(MensajePago mensaje) {
        String id = mensaje.correlationId();
        return (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id.trim();
    }

    private ResultadoEnvio desdeError(String llave, FeignException e) {
        int status = e.status();
        BankError error = leerError(e);
        String codigo = error != null ? error.code() : null;

        // Duplicado: el banco ya tiene este pago, se trata como entregado
        if (status == 409 && "DUPLICATE_PAYMENT".equals(codigo)) {
            String recibo = error.originalReceipt() != null ? error.originalReceipt().receiptId() : null;
            return ResultadoEnvio.exito(llave, 409, recibo, "El banco ya tenia este pago: se trata como entregado");
        }

        // status <= 0: no hubo respuesta (timeout o conexion), puede reintentarse
        boolean sinRespuesta = status <= 0;
        boolean reintentable = sinRespuesta || REINTENTABLES.contains(status);
        String detalle = sinRespuesta
                ? "Sin respuesta del banco (timeout o conexion)"
                : "El banco respondio " + status + (codigo != null ? " " + codigo : "");
        return ResultadoEnvio.fallo(llave, sinRespuesta ? null : status, codigo, detalle, reintentable);
    }

    private BankError leerError(FeignException e) {
        String cuerpo = e.contentUTF8();
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