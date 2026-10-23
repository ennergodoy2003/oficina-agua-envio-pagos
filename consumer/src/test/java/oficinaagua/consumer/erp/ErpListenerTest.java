package oficinaagua.consumer.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import oficinaagua.consumer.logs.LogEnvioRepository;
import oficinaagua.consumer.logs.RegistroIntento;
import oficinaagua.consumer.pago.Destino;
import oficinaagua.consumer.pago.MensajePago;
import oficinaagua.consumer.reintentos.DeadLetterPublicador;
import oficinaagua.consumer.reintentos.PoliticaReintentos;
import oficinaagua.consumer.reintentos.Reintentador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

/** HU-45/46/47/48 en el ERP: anota cada intento, no reenvia lo entregado, reintenta y manda a la DLQ con el motivo. */
class ErpListenerTest {

    private static final ErpComprobante COMPROBANTE = new ErpComprobante("ERP-20261020-Q7W2ZX",
            "EQA-000001", "EQUIPO_A", "CONTABILIZADO", "150.75",
            OffsetDateTime.parse("2026-10-20T18:00:00Z"), new ErpPeriodo(2026, 10), "PDA-2026-000873");

    private static final ResultadoEnvioErp EXITO = ResultadoEnvioErp.exito(
            "EQA-000001", 201, COMPROBANTE, "Contabilizado en el ERP", "corr-1");
    private static final ResultadoEnvioErp CAIDO = ResultadoEnvioErp.fallo(
            "EQA-000001", 503, "SERVICIO_NO_DISPONIBLE", "El ERP respondio 503 SERVICIO_NO_DISPONIBLE", true, "corr-2");
    private static final ResultadoEnvioErp RECHAZO = ResultadoEnvioErp.fallo(
            "EQA-000001", 422, "TOTAL_NO_CUADRA", "El ERP respondio 422 TOTAL_NO_CUADRA", false, "corr-3");

    private EnvioErpService servicio;
    private LogEnvioRepository logs;
    private DeadLetterPublicador deadLetter;
    private ErpListener listener;

    @BeforeEach
    void preparar() {
        servicio = mock(EnvioErpService.class);
        logs = mock(LogEnvioRepository.class);
        deadLetter = mock(DeadLetterPublicador.class);
        // Reintentador real: maximo 3 intentos y sin esperar de verdad
        Reintentador reintentador = new Reintentador(new PoliticaReintentos(3, 1000, 2, 30000), ms -> true);
        listener = new ErpListener(servicio, logs, reintentador, deadLetter);
    }

    @Test
    void exito_seAnotaConDestinoErpYSuComprobante_yHaceAck() {
        when(servicio.enviar(any())).thenReturn(EXITO);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        ArgumentCaptor<RegistroIntento> captor = ArgumentCaptor.forClass(RegistroIntento.class);
        verify(logs).guardar(captor.capture());
        RegistroIntento r = captor.getValue();
        assertThat(r.destino()).isEqualTo(Destino.ERP);
        assertThat(r.resultado()).isEqualTo(RegistroIntento.EXITOSO);
        assertThat(r.numeroComprobante()).isEqualTo("ERP-20261020-Q7W2ZX");
        assertThat(r.numeroPartida()).isEqualTo("PDA-2026-000873");
        assertThat(r.claveIntento()).startsWith("ERP#");
        verify(deadLetter, never()).enviar(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void caidoUnaVez_luegoExito_noVaADeadLetter() {
        when(servicio.enviar(any())).thenReturn(CAIDO, EXITO);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio, times(2)).enviar(any());
        verify(logs, times(2)).guardar(any());
        verify(deadLetter, never()).enviar(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void caidoSiempre_reintentaHastaElMaximo_yVaADeadLetterConElMotivo() {
        when(servicio.enviar(any())).thenReturn(CAIDO);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio, times(3)).enviar(any());
        verify(logs, times(3)).guardar(any());
        verify(deadLetter).enviar(any(), eq(Destino.ERP), eq("El ERP respondio 503 SERVICIO_NO_DISPONIBLE"),
                eq("SERVICIO_NO_DISPONIBLE"), eq(503), eq(3));
    }

    @Test
    void rechazoNoReintentable_vaDirectoADeadLetter() {
        when(servicio.enviar(any())).thenReturn(RECHAZO);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio, times(1)).enviar(any());
        verify(deadLetter).enviar(any(), eq(Destino.ERP), eq("El ERP respondio 422 TOTAL_NO_CUADRA"),
                eq("TOTAL_NO_CUADRA"), eq(422), eq(1));
    }

    @Test
    void siNoSePuedePublicarEnLaDeadLetter_seRechazaElMensaje() {
        when(servicio.enviar(any())).thenReturn(RECHAZO);
        doThrow(new AmqpException("RabbitMQ caido"))
                .when(deadLetter).enviar(any(), any(), any(), any(), any(), anyInt());

        assertThatThrownBy(() -> listener.recibir(mensaje()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void dynamoCaido_unExitoIgualHaceAck() {
        when(servicio.enviar(any())).thenReturn(EXITO);
        doThrow(DynamoDbException.builder().message("DynamoDB caido").build()).when(logs).guardar(any());

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();
        verify(deadLetter, never()).enviar(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void dynamoCaido_unRechazoIgualVaADeadLetter() {
        when(servicio.enviar(any())).thenReturn(RECHAZO);
        doThrow(DynamoDbException.builder().message("DynamoDB caido").build()).when(logs).guardar(any());

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();
        verify(deadLetter).enviar(any(), eq(Destino.ERP), any(), any(), any(), anyInt());
    }

    @Test
    void pagoYaEntregado_noSeReenvia_yDejaLineaDeRemarca() {
        when(logs.yaEntregado("EQA-000001", Destino.ERP)).thenReturn(true);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio, never()).enviar(any()); // no se llama al ERP
        ArgumentCaptor<RegistroIntento> captor = ArgumentCaptor.forClass(RegistroIntento.class);
        verify(logs).guardar(captor.capture()); // una sola linea, para que la Lambda re-marque
        RegistroIntento r = captor.getValue();
        assertThat(r.destino()).isEqualTo(Destino.ERP);
        assertThat(r.resultado()).isEqualTo(RegistroIntento.EXITOSO);
        assertThat(r.httpStatus()).isNull();
        assertThat(r.detalle()).contains("se vuelve a marcar");
    }

    @Test
    void pagoYaEntregado_dynamoCaidoAlRemarcar_igualHaceAck() {
        when(logs.yaEntregado("EQA-000001", Destino.ERP)).thenReturn(true);
        doThrow(DynamoDbException.builder().message("DynamoDB caido").build()).when(logs).guardar(any());

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();
        verify(servicio, never()).enviar(any());
    }
    @Test
    void siNoSePuedeRevisarDynamo_seEnviaIgual() {
        when(logs.yaEntregado(any(), any()))
                .thenThrow(DynamoDbException.builder().message("DynamoDB caido").build());
        when(servicio.enviar(any())).thenReturn(EXITO);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio).enviar(any());
    }

    private static MensajePago mensaje() {
        return new MensajePago("EQA-000001", Destino.ERP, "CLI-0001", "Ana Lopez", "MTR-00001",
                2026, 9, 15075, 15075, 0, null, LocalDateTime.of(2026, 10, 19, 10, 30), "efectivo", null);
    }
}