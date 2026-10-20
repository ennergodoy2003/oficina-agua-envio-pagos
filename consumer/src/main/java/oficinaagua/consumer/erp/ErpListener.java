package oficinaagua.consumer.erp;

import oficinaagua.consumer.logs.LogEnvioRepository;
import oficinaagua.consumer.logs.RegistroIntento;
import oficinaagua.consumer.pago.Destino;
import oficinaagua.consumer.pago.MensajePago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** El cartero del ERP: toma una carta de pagos.erp, la entrega y anota el intento. */
@Component
public class ErpListener {

    private static final Logger log = LoggerFactory.getLogger(ErpListener.class);

    private final EnvioErpService servicio;
    private final LogEnvioRepository logs;

    public ErpListener(EnvioErpService servicio, LogEnvioRepository logs) {
        this.servicio = servicio;
        this.logs = logs;
    }

    @RabbitListener(queues = "pagos.erp")
    public void recibir(MensajePago mensaje) {
        ResultadoEnvioErp resultado = servicio.enviar(mensaje);
        anotar(resultado); // HU-46: una linea por intento, salga bien o mal

        if (resultado.exitoso()) {
            log.info("Pago {} contabilizado en el ERP: {} (comprobante {}, partida {})",
                    resultado.numeroDocumento(), resultado.detalle(),
                    resultado.numeroComprobante(), resultado.numeroPartida());
            return; // termina sin excepcion: Spring hace ack
        }

        log.warn("Pago {} NO contabilizado en el ERP: {} (reintentable: {})",
                resultado.numeroDocumento(), resultado.detalle(), resultado.reintentable());
        // sin ack y sin reencolar: el mensaje va a pagos.dlx -> pagos.dlq
        throw new AmqpRejectAndDontRequeueException(resultado.detalle());
    }

    /** Igual que en el banco: si DynamoDB falla, se avisa pero NO cambia la decision del envio. */
    private void anotar(ResultadoEnvioErp r) {
        try {
            logs.guardar(RegistroIntento.nuevo(r.numeroDocumento(), Destino.ERP, r.exitoso(),
                    r.httpStatus(), r.codigoError(), r.detalle(), r.correlationId(),
                    null, r.numeroComprobante(), r.numeroPartida()));
        } catch (RuntimeException e) {
            log.error("No se pudo anotar en DynamoDB el intento del pago {}: {}",
                    r.numeroDocumento(), e.getMessage());
        }
    }
}