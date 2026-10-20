package oficinaagua.consumer.banco;

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

/** HU-46/47/48: anota cada intento, no reenvia lo entregado, reintenta y manda a la DLQ con el motivo. */
class BancoListenerTest {

    private static final ResultadoEnvio EXITO = ResultadoEnvio.exito(
            "EQA-000001", 201, "BNK-123", "Entregado al banco", "corr-1");
    private static final ResultadoEnvio CAIDO = ResultadoEnvio.fallo(
            "EQA-000001", 503, "SERVICE_UNAVAILABLE", "El banco respondio 503 SERVICE_UNAVAILABLE", true, "corr-2");
    private static final ResultadoEnvio RECHAZO = ResultadoEnvio.fallo(
            "EQA-000001", 400, "VALIDATION_ERROR", "El banco respondio 400 VALIDATION_ERROR", false, "corr-3");

    private EnvioBancoService servicio;
    private LogEnvioRepository logs;
    private DeadLetterPublicador deadLetter;
    private BancoListener listener;

    @BeforeEach
    void preparar() {
        servicio = mock(EnvioBancoService.class);
        logs = mock(LogEnvioRepository.class);
        deadLetter = mock(DeadLetterPublicador.class);
        // Reintentador real: maximo 3 intentos y sin esperar de verdad
        Reintentador reintentador = new Reintentador(new PoliticaReintentos(3, 1000, 2, 30000), ms -> true);
        listener = new BancoListener(servicio, logs, reintentador, deadLetter);
    }

    @Test
    void exito_seAnotaUnaVez_yHaceAck() {
        when(servicio.enviar(any())).thenReturn(EXITO);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        ArgumentCaptor<RegistroIntento> captor = ArgumentCaptor.forClass(RegistroIntento.class);
        verify(logs).guardar(captor.capture());
        assertThat(captor.getValue().destino()).isEqualTo(Destino.BANCO);
        assertThat(captor.getValue().resultado()).isEqualTo(RegistroIntento.EXITOSO);
        assertThat(captor.getValue().receiptId()).isEqualTo("BNK-123");
        verify(deadLetter, never()).enviar(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void caidoUnaVez_luegoExito_noVaADeadLetter() {
        when(servicio.enviar(any())).thenReturn(CAIDO, EXITO);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio, times(2)).enviar(any());
        verify(logs, times(2)).guardar(any()); // una linea por intento
        verify(deadLetter, never()).enviar(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void caidoSiempre_reintentaHastaElMaximo_yVaADeadLetterConElMotivo() {
        when(servicio.enviar(any())).thenReturn(CAIDO);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio, times(3)).enviar(any());
        verify(logs, times(3)).guardar(any());
        verify(deadLetter).enviar(any(), eq(Destino.BANCO), eq("El banco respondio 503 SERVICE_UNAVAILABLE"),
                eq("SERVICE_UNAVAILABLE"), eq(503), eq(3));
    }

    @Test
    void rechazoNoReintentable_vaDirectoADeadLetter() {
        when(servicio.enviar(any())).thenReturn(RECHAZO);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio, times(1)).enviar(any());
        verify(deadLetter).enviar(any(), eq(Destino.BANCO), eq("El banco respondio 400 VALIDATION_ERROR"),
                eq("VALIDATION_ERROR"), eq(400), eq(1));
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
        verify(deadLetter).enviar(any(), eq(Destino.BANCO), any(), any(), any(), anyInt());
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
        when(servicio.enviar(any())).thenReturn(EXITO);

        assertThatCode(() -> listener.recibir(mensaje())).doesNotThrowAnyException();

        verify(servicio).enviar(any());
    }

    private static MensajePago mensaje() {
        return new MensajePago("EQA-000001", Destino.BANCO, "CLI-0001", "Ana Lopez", "CONT-1",
                2026, 9, 15075, 15075, 0, null, LocalDateTime.of(2026, 10, 19, 10, 30), "efectivo", null);
    }
}