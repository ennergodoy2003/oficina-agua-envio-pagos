package oficinaagua.consumer.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import oficinaagua.consumer.logs.LogEnvioRepository;
import oficinaagua.consumer.logs.RegistroIntento;
import oficinaagua.consumer.pago.Destino;
import oficinaagua.consumer.pago.MensajePago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import static org.mockito.Mockito.never;
/**
 * HU-46: el listener anota cada intento y el registro NUNCA cambia la decision del envio.
 * Sin Spring, sin Docker: el servicio del banco y el repositorio son mocks.
 */
class BancoListenerTest {

    private EnvioBancoService servicio;
    private LogEnvioRepository logs;
    private BancoListener listener;

    @BeforeEach
    void preparar() {
        servicio = mock(EnvioBancoService.class);
        logs = mock(LogEnvioRepository.class);
        listener = new BancoListener(servicio, logs);
    }

    @Test
    void exito_seAnotaComoExitoso_yHaceAck() {
        when(servicio.enviar(any())).thenReturn(
                ResultadoEnvio.exito("EQA-000001", 201, "BNK-123", "Entregado al banco", "corr-1"));

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        RegistroIntento r = registroGuardado();
        assertThat(r.numeroDocumento()).isEqualTo("EQA-000001");
        assertThat(r.destino()).isEqualTo(Destino.BANCO);
        assertThat(r.resultado()).isEqualTo(RegistroIntento.EXITOSO);
        assertThat(r.httpStatus()).isEqualTo(201);
        assertThat(r.receiptId()).isEqualTo("BNK-123");
        assertThat(r.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void fallo_seAnotaComoFallido_yVaADeadLetter() {
        when(servicio.enviar(any())).thenReturn(ResultadoEnvio.fallo("EQA-000001", 503,
                "SERVICE_UNAVAILABLE", "El banco respondio 503", true, "corr-2"));

        assertThatThrownBy(() -> listener.recibir(mensaje()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        RegistroIntento r = registroGuardado();
        assertThat(r.resultado()).isEqualTo(RegistroIntento.FALLIDO);
        assertThat(r.httpStatus()).isEqualTo(503);
        assertThat(r.codigoError()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(r.receiptId()).isNull();
        assertThat(r.correlationId()).isEqualTo("corr-2");
    }

    @Test
    void dynamoCaido_unExitoIgualHaceAck() {
        when(servicio.enviar(any())).thenReturn(
                ResultadoEnvio.exito("EQA-000001", 201, "BNK-123", "Entregado al banco", "corr-3"));
        doThrow(DynamoDbException.builder().message("DynamoDB caido").build())
                .when(logs).guardar(any());

        // El banco ya tiene el pago: aunque no se pudo anotar, NO debe ir a dead-letter
        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();
    }

    @Test
    void dynamoCaido_unFalloIgualVaADeadLetter() {
        when(servicio.enviar(any())).thenReturn(ResultadoEnvio.fallo("EQA-000001", 503,
                "SERVICE_UNAVAILABLE", "El banco respondio 503", true, "corr-4"));
        doThrow(DynamoDbException.builder().message("DynamoDB caido").build())
                .when(logs).guardar(any());

        // La decision sigue siendo la del banco, no la de DynamoDB
        assertThatThrownBy(() -> listener.recibir(mensaje()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    /** Atrapa la linea que el listener le paso al repositorio. */
    private RegistroIntento registroGuardado() {
        ArgumentCaptor<RegistroIntento> captor = ArgumentCaptor.forClass(RegistroIntento.class);
        verify(logs).guardar(captor.capture());
        return captor.getValue();
    }

    /** El contenido no importa: el servicio es un mock y responde lo que cada prueba programa. */
    private static MensajePago mensaje() {
        return new MensajePago("EQA-000001", Destino.BANCO, "CLI-0001", "Ana Lopez", "CONT-1",
                2026, 9, 15075, 15075, 0, null, LocalDateTime.of(2026, 10, 19, 10, 30), "efectivo",
                null);
    }
    @Test
    void pagoYaEntregado_noSeReenvia_yHaceAck() {
        when(logs.yaEntregado("EQA-000001", Destino.BANCO)).thenReturn(true);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio, never()).enviar(any());
        verify(logs, never()).guardar(any());
    }

    @Test
    void siNoSePuedeRevisarDynamo_seEnviaIgual() {
        when(logs.yaEntregado(any(), any()))
                .thenThrow(DynamoDbException.builder().message("DynamoDB caido").build());
        when(servicio.enviar(any())).thenReturn(
                ResultadoEnvio.exito("EQA-000001", 201, "BNK-123", "Entregado al banco", "corr-5"));

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio).enviar(any());
    }
}