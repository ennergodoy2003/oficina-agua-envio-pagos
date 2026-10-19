package oficinaagua.producer.envio;

import java.util.List;
import oficinaagua.producer.pago.EstadoEnvio;
import oficinaagua.producer.pago.MensajePago;
import oficinaagua.producer.pago.Pago;
import oficinaagua.producer.pago.PagoRepository;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class EnvioService {

    private static final String EXCHANGE = "pagos.exchange";
    private static final long CONFIRMACION_MS = 5000;

    private final PagoRepository pagoRepository;
    private final RabbitTemplate rabbitTemplate;

    public EnvioService(PagoRepository pagoRepository, RabbitTemplate rabbitTemplate) {
        this.pagoRepository = pagoRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public DisparoEnvioResponse disparar(int limite) {
        List<Pago> pagos = pagoRepository.buscarPendientes(
                EstadoEnvio.PENDIENTE, PageRequest.of(0, limite));
        List<MensajePago> mensajes = pagos.stream()
                .flatMap(pago -> MensajePago.pendientesDe(pago).stream())
                .toList();

        if (mensajes.isEmpty()) {
            return new DisparoEnvioResponse(pagos.size(), 0);
        }

        try {
            rabbitTemplate.invoke(operaciones -> {
                mensajes.forEach(m ->
                        operaciones.convertAndSend(EXCHANGE, m.destino().routingKey(), m));
                operaciones.waitForConfirmsOrDie(CONFIRMACION_MS);
                return null;
            });
        } catch (AmqpException e) {
            throw new ColaNoDisponibleException("RabbitMQ no confirmó la publicación", e);
        }

        return new DisparoEnvioResponse(pagos.size(), mensajes.size());
    }
}