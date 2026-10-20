package oficinaagua.consumer.reintentos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.GetResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * HU-48: devuelve mensajes de pagos.dlq a su cola original cuando el destino vuelve.
 * Primero publica y despues confirma: si algo falla en medio, el mensaje sigue en la DLQ.
 */
@Service
public class ReprocesadorDlq {

    private static final Logger log = LoggerFactory.getLogger(ReprocesadorDlq.class);

    static final String COLA_DLQ = "pagos.dlq";
    static final String EXCHANGE = "pagos.exchange";

    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;

    public ReprocesadorDlq(RabbitTemplate rabbit, ObjectMapper objectMapper) {
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
    }

    public ResultadoReproceso reprocesar(int limite) {
        return rabbit.execute(canal -> {
            int reprocesados = 0;
            for (int i = 0; i < limite; i++) {
                GetResponse mensaje = canal.basicGet(COLA_DLQ, false); // false = sin ack automatico
                if (mensaje == null) {
                    break; // la DLQ quedo vacia
                }
                long tag = mensaje.getEnvelope().getDeliveryTag();
                String routingKey = routingKeyDe(mensaje.getBody());
                if (routingKey == null) {
                    log.warn("Mensaje de la DLQ sin destino valido: se deja en la DLQ");
                    canal.basicNack(tag, false, true); // true = vuelve a la DLQ
                    break;
                }
                canal.basicPublish(EXCHANGE, routingKey, mensaje.getProps(), mensaje.getBody());
                canal.basicAck(tag, false); // solo despues de publicar
                reprocesados++;
            }
            long quedan = canal.messageCount(COLA_DLQ);
            log.info("Reproceso de la DLQ: {} mensaje(s) devueltos, quedan {}", reprocesados, quedan);
            return new ResultadoReproceso(reprocesados, quedan);
        });
    }

    /** Lee el campo destino del JSON: BANCO -> banco, ERP -> erp. */
    private String routingKeyDe(byte[] cuerpo) {
        try {
            String destino = objectMapper.readTree(cuerpo).path("destino").asText("");
            return switch (destino) {
                case "BANCO" -> "banco";
                case "ERP" -> "erp";
                default -> null;
            };
        } catch (IOException e) {
            return null;
        }
    }
}