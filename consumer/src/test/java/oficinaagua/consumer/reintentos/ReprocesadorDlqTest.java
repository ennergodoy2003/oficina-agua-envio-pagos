package oficinaagua.consumer.reintentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** HU-48: el reproceso devuelve cada mensaje a su cola, y publica ANTES de confirmar. */
class ReprocesadorDlqTest {

    private Channel canal;
    private ReprocesadorDlq reprocesador;

    @BeforeEach
    void preparar() throws Exception {
        canal = mock(Channel.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        // rabbit.execute(callback) ejecuta el callback con el canal simulado
        when(rabbit.execute(any())).thenAnswer(inv -> ((ChannelCallback<?>) inv.getArgument(0)).doInRabbit(canal));
        reprocesador = new ReprocesadorDlq(rabbit, new ObjectMapper());
    }

    @Test
    void devuelveCadaMensajeASuCola_yPublicaAntesDeConfirmar() throws Exception {
        when(canal.basicGet("pagos.dlq", false))
                .thenReturn(mensaje(1, "BANCO"), mensaje(2, "ERP"), null);

        ResultadoReproceso resultado = reprocesador.reprocesar(20);

        assertThat(resultado.reprocesados()).isEqualTo(2);
        InOrder orden = inOrder(canal);
        orden.verify(canal).basicPublish(eq("pagos.exchange"), eq("banco"), any(), any());
        orden.verify(canal).basicAck(1, false);
        orden.verify(canal).basicPublish(eq("pagos.exchange"), eq("erp"), any(), any());
        orden.verify(canal).basicAck(2, false);
    }

    @Test
    void respetaElLimite() throws Exception {
        when(canal.basicGet("pagos.dlq", false)).thenReturn(mensaje(1, "BANCO"), mensaje(2, "BANCO"));

        ResultadoReproceso resultado = reprocesador.reprocesar(1);

        assertThat(resultado.reprocesados()).isEqualTo(1);
        verify(canal).basicAck(1, false);
        verify(canal, never()).basicAck(2, false);
    }

    @Test
    void mensajeSinDestinoValido_seQuedaEnLaDlq() throws Exception {
        when(canal.basicGet("pagos.dlq", false)).thenReturn(mensaje(1, "OTRO"));

        ResultadoReproceso resultado = reprocesador.reprocesar(20);

        assertThat(resultado.reprocesados()).isZero();
        verify(canal).basicNack(1, false, true);
        verify(canal, never()).basicPublish(anyString(), anyString(), any(), any());
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }

    private static GetResponse mensaje(long tag, String destino) {
        byte[] cuerpo = ("{\"numeroDocumento\":\"EQA-000001\",\"destino\":\"" + destino + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return new GetResponse(new Envelope(tag, false, "pagos.dlx", ""),
                new AMQP.BasicProperties(), cuerpo, 0);
    }
}