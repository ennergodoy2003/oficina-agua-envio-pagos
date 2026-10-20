package oficinaagua.consumer.banco;

import oficinaagua.consumer.logs.LogEnvioRepository;
import oficinaagua.consumer.logs.RegistroIntento;
import oficinaagua.consumer.pago.Destino;
import oficinaagua.consumer.pago.MensajePago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** El cartero del banco: toma una carta de pagos.banco, la entrega y anota el intento. */
@Component
public class BancoListener {

    private static final Logger log = LoggerFactory.getLogger(BancoListener.class);

    private final EnvioBancoService servicio;
    private final LogEnvioRepository logs;

    public BancoListener(EnvioBancoService servicio, LogEnvioRepository logs) {
        this.servicio = servicio;
        this.logs = logs;
    }

    @RabbitListener(queues = "pagos.banco")
    public void recibir(MensajePago mensaje) {
        ResultadoEnvio resultado = servicio.enviar(mensaje);
        anotar(resultado); // HU-46: una linea por intento, salga bien o mal

        if (resultado.exitoso()) {
            log.info("Pago {} entregado al banco: {} (comprobante {})",
                    resultado.numeroDocumento(), resultado.detalle(), resultado.receiptId());
            return; // termina sin excepcion: Spring hace ack
        }

        log.warn("Pago {} NO entregado al banco: {} (reintentable: {})",
                resultado.numeroDocumento(), resultado.detalle(), resultado.reintentable());
        // sin ack y sin reencolar: el mensaje va a pagos.dlx -> pagos.dlq
        throw new AmqpRejectAndDontRequeueException(resultado.detalle());
    }

    /**
     * Si DynamoDB falla, se avisa en el log pero NO cambia la decision del envio.
     * Si un exito no quedo anotado, la Lambda no marca el pago, el producer lo republica
     * y el banco responde 409 DUPLICATE_PAYMENT: ese intento si se anota.
     */
    private void anotar(ResultadoEnvio r) {
        try {
            logs.guardar(RegistroIntento.nuevo(r.numeroDocumento(), Destino.BANCO, r.exitoso(),
                    r.httpStatus(), r.codigoError(), r.detalle(), r.correlationId(),
                    r.receiptId(), null, null));
        } catch (RuntimeException e) {
            log.error("No se pudo anotar en DynamoDB el intento del pago {}: {}",
                    r.numeroDocumento(), e.getMessage());
        }
    }
}