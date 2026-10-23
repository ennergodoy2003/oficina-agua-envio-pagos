package oficinaagua.consumer.banco;

import oficinaagua.consumer.logs.LogEnvioRepository;
import oficinaagua.consumer.logs.RegistroIntento;
import oficinaagua.consumer.pago.Destino;
import oficinaagua.consumer.pago.MensajePago;
import oficinaagua.consumer.reintentos.DeadLetterPublicador;
import oficinaagua.consumer.reintentos.Reintentador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** El cartero del banco: toma una carta de pagos.banco, la entrega (con reintentos) y anota cada intento. */
@Component
public class BancoListener {

    private static final Logger log = LoggerFactory.getLogger(BancoListener.class);

    private final EnvioBancoService servicio;
    private final LogEnvioRepository logs;
    private final Reintentador reintentador;
    private final DeadLetterPublicador deadLetter;

    public BancoListener(EnvioBancoService servicio, LogEnvioRepository logs,
                         Reintentador reintentador, DeadLetterPublicador deadLetter) {
        this.servicio = servicio;
        this.logs = logs;
        this.reintentador = reintentador;
        this.deadLetter = deadLetter;
    }

    @RabbitListener(queues = "pagos.banco")
    public void recibir(MensajePago mensaje) {
        if (yaEntregado(mensaje.numeroDocumento())) {
            log.info("Pago {} ya estaba entregado al banco: no se vuelve a enviar", mensaje.numeroDocumento());
            reanotar(mensaje);
            return; // ack: se descarta el mensaje repetido (HU-47)
        }

        // HU-48: reintenta con backoff; anotar se llama en cada intento (HU-46)
        Reintentador.Desenlace<ResultadoEnvio> desenlace =
                reintentador.ejecutar(() -> servicio.enviar(mensaje), this::anotar);
        ResultadoEnvio resultado = desenlace.resultado();

        if (resultado.exitoso()) {
            log.info("Pago {} entregado al banco: {} (comprobante {})",
                    resultado.numeroDocumento(), resultado.detalle(), resultado.receiptId());
            return; // ack
        }

        log.warn("Pago {} NO entregado al banco tras {} intento(s): {}",
                resultado.numeroDocumento(), desenlace.intentos(), resultado.detalle());
        aDeadLetter(mensaje, resultado, desenlace.intentos());
    }

    /** HU-48: a la DLQ con el motivo. Si no se puede publicar, se rechaza y RabbitMQ lo manda igual (sin motivo). */
    private void aDeadLetter(MensajePago mensaje, ResultadoEnvio r, int intentos) {
        try {
            deadLetter.enviar(mensaje, Destino.BANCO, r.detalle(), r.codigoError(), r.httpStatus(), intentos);
        } catch (AmqpException e) {
            throw new AmqpRejectAndDontRequeueException(r.detalle(), e);
        }
    }

    /** Si DynamoDB falla, se avisa en el log pero NO cambia la decision del envio. */
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

    /** Linea EXITOSA de re-marca: no se reenvia, pero hace que la Lambda vuelva a poner el sello. */
    private void reanotar(MensajePago m) {
        try {
            logs.guardar(RegistroIntento.nuevo(m.numeroDocumento(), Destino.BANCO, true,
                    null, null, "Ya entregado antes: no se reenvia, se vuelve a marcar",
                    m.correlationId(), null, null, null));
        } catch (RuntimeException e) {
            log.error("No se pudo re-anotar en DynamoDB el pago {}: {}", m.numeroDocumento(), e.getMessage());
        }
    }

    /** HU-47: si DynamoDB no responde, se envia igual; la llave estable evita el duplicado. */
    private boolean yaEntregado(String numeroDocumento) {
        try {
            return logs.yaEntregado(numeroDocumento, Destino.BANCO);
        } catch (RuntimeException e) {
            log.warn("No se pudo revisar en DynamoDB el pago {}: se envia igual", numeroDocumento);
            return false;
        }
    }
}