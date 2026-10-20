package oficinaagua.consumer.banco;

import oficinaagua.consumer.pago.MensajePago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** El cartero del banco: toma una carta de pagos.banco y la entrega. */
@Component
public class BancoListener {

    private static final Logger log = LoggerFactory.getLogger(BancoListener.class);

    private final EnvioBancoService servicio;

    public BancoListener(EnvioBancoService servicio) {
        this.servicio = servicio;
    }

    @RabbitListener(queues = "pagos.banco")
    public void recibir(MensajePago mensaje) {
        ResultadoEnvio resultado = servicio.enviar(mensaje);

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
}