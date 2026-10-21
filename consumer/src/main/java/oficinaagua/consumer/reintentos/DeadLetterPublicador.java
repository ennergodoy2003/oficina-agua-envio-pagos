package oficinaagua.consumer.reintentos;

import oficinaagua.consumer.pago.Destino;
import oficinaagua.consumer.pago.MensajePago;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * HU-48: manda el mensaje a la dead-letter con el motivo en los headers.
 * pagos.dlx es fanout: todo lo que llega va a pagos.dlq.
 */
@Component
public class DeadLetterPublicador {

    static final String EXCHANGE_DLX = "pagos.dlx";

    private final RabbitTemplate rabbit;

    public DeadLetterPublicador(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void enviar(MensajePago mensaje, Destino destino, String motivo, String codigoError,
                       Integer httpStatus, int intentos) {
        rabbit.convertAndSend(EXCHANGE_DLX, "", mensaje, m -> {
            var headers = m.getMessageProperties();
            headers.setHeader("destino", destino.name());
            headers.setHeader("motivo-fallo", motivo);
            headers.setHeader("intentos", intentos);
            if (codigoError != null) {
                headers.setHeader("codigo-error", codigoError);
            }
            if (httpStatus != null) {
                headers.setHeader("http-status", httpStatus);
            }
            return m;
        });
    }
}