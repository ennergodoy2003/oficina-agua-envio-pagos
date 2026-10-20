package oficinaagua.consumer.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import oficinaagua.consumer.logs.LogEnvioRepository;
import oficinaagua.consumer.logs.RegistroIntento;
import oficinaagua.consumer.pago.Destino;
import oficinaagua.consumer.pago.MensajePago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

/**
 * HU-45 + HU-46: el listener del ERP anota cada intento con Destino.ERP y su comprobante,
 * y el registro NUNCA cambia la decision del envio. Sin Spring, sin Docker.
 */
class ErpListenerTest {

    private static final ErpComprobante COMPROBANTE = new ErpComprobante("ERP-20261020-Q7W2ZX",
            "EQA-000001", "EQUIPO_A", "CONTABILIZADO", "150.75",
            OffsetDateTime.parse("2026-10-20T18:00:00Z"), new ErpPeriodo(2026, 10), "PDA-2026-000873");

    private EnvioErpService servicio;
    private LogEnvioRepository logs;
    private ErpListener listener;

    @BeforeEach
    void preparar() {
        servicio = mock(EnvioErpService.class);
        logs = mock(LogEnvioRepository.class);
        listener = new ErpListener(servicio, logs);
    }

    @Test
    void exito_seAnotaConDestinoErpYSuComprobante_yHaceAck() {
        when(servicio.enviar(any())).thenReturn(
                ResultadoEnvioErp.exito("EQA-000001", 201, COMPROBANTE, "Contabilizado en el ERP", "corr-1"));

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        RegistroIntento r = registroGuardado();
        assertThat(r.destino()).isEqualTo(Destino.ERP);
        assertThat(r.resultado()).isEqualTo(RegistroIntento.EXITOSO);
        assertThat(r.httpStatus()).isEqualTo(201);
        assertThat(r.numeroComprobante()).isEqualTo("ERP-20261020-Q7W2ZX");
        assertThat(r.numeroPartida()).isEqualTo("PDA-2026-000873");
        assertThat(r.receiptId()).isNull();
        assertThat(r.claveIntento()).startsWith("ERP#");
        assertThat(r.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void fallo_seAnotaComoFallido_yVaADeadLetter() {
        when(servicio.enviar(any())).thenReturn(ResultadoEnvioErp.fallo("EQA-000004", 503,
                "SERVICIO_NO_DISPONIBLE", "El ERP respondio 503 SERVICIO_NO_DISPONIBLE", true, "corr-2"));

        assertThatThrownBy(() -> listener.recibir(mensaje()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        RegistroIntento r = registroGuardado();
        assertThat(r.destino()).isEqualTo(Destino.ERP);
        assertThat(r.resultado()).isEqualTo(RegistroIntento.FALLIDO);
        assertThat(r.httpStatus()).isEqualTo(503);
        assertThat(r.codigoError()).isEqualTo("SERVICIO_NO_DISPONIBLE");
        assertThat(r.numeroComprobante()).isNull();
    }

    @Test
    void dynamoCaido_unExitoIgualHaceAck() {
        when(servicio.enviar(any())).thenReturn(
                ResultadoEnvioErp.exito("EQA-000001", 201, COMPROBANTE, "Contabilizado en el ERP", "corr-3"));
        doThrow(DynamoDbException.builder().message("DynamoDB caido").build())
                .when(logs).guardar(any());

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();
    }

    @Test
    void dynamoCaido_unFalloIgualVaADeadLetter() {
        when(servicio.enviar(any())).thenReturn(ResultadoEnvioErp.fallo("EQA-000004", 503,
                "SERVICIO_NO_DISPONIBLE", "El ERP respondio 503 SERVICIO_NO_DISPONIBLE", true, "corr-4"));
        doThrow(DynamoDbException.builder().message("DynamoDB caido").build())
                .when(logs).guardar(any());

        assertThatThrownBy(() -> listener.recibir(mensaje()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private RegistroIntento registroGuardado() {
        ArgumentCaptor<RegistroIntento> captor = ArgumentCaptor.forClass(RegistroIntento.class);
        verify(logs).guardar(captor.capture());
        return captor.getValue();
    }

    /** El contenido no importa: el servicio es un mock. */
    private static MensajePago mensaje() {
        return new MensajePago("EQA-000001", Destino.ERP, "CLI-0001", "Ana Lopez", "MTR-00001",
                2026, 9, 15075, 15075, 0, null, LocalDateTime.of(2026, 10, 19, 10, 30), "efectivo", null);
    }

    @Test
    void pagoYaEntregado_noSeReenvia_yHaceAck() {
        when(logs.yaEntregado("EQA-000001", Destino.ERP)).thenReturn(true);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio, never()).enviar(any());
        verify(logs, never()).guardar(any());
    }

    @Test
    void siNoSePuedeRevisarDynamo_seEnviaIgual() {
        when(logs.yaEntregado(any(), any()))
                .thenThrow(DynamoDbException.builder().message("DynamoDB caido").build());
        when(servicio.enviar(any())).thenReturn(
                ResultadoEnvioErp.exito("EQA-000001", 201, COMPROBANTE, "Contabilizado en el ERP", "corr-5"));

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio).enviar(any());
    }
}