package oficinaagua.consumer.banco;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.LocalDateTime;
import oficinaagua.consumer.pago.Destino;
import oficinaagua.consumer.pago.MensajePago;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Prueba contra un banco falso (WireMock). No toca RabbitMQ, el .env ni el simulador del docente. */
@SpringBootTest(properties = {
        "spring.rabbitmq.username=test",
        "spring.rabbitmq.password=test",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "banco.api-key=clave-de-prueba",
        "banco.team-code=EQUIPO_A"
})
class EnvioBancoServiceTest {

    private static final String RUTA = "/bank/v1/payments";

    private static final String RECIBO = """
            {"receiptId":"BNK-20261019-ABC123","idempotencyKey":"EQA-000001","teamCode":"EQUIPO_A",
             "status":"ACCEPTED","amount":{"valueInCents":15075,"currency":"GTQ"},
             "receivedAt":"2026-10-19T16:30:00Z","valueDate":"2026-10-20"}""";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("banco.url", wireMock::baseUrl);
    }

    @Autowired
    EnvioBancoService servicio;

    @Test
    void respuesta201_esExito_yLaPeticionLlevaLoQueDiceElContrato() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(201, RECIBO)));

        ResultadoEnvio resultado = servicio.enviar(mensaje("efectivo"));

        assertThat(resultado.exitoso()).isTrue();
        assertThat(resultado.receiptId()).isEqualTo("BNK-20261019-ABC123");
        wireMock.verify(postRequestedFor(urlEqualTo(RUTA))
                .withHeader("Idempotency-Key", equalTo("EQA-000001"))
                .withHeader("x-api-key", equalTo("clave-de-prueba"))
                .withRequestBody(matchingJsonPath("$.teamCode", equalTo("EQUIPO_A")))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("CASH")))
                .withRequestBody(matchingJsonPath("$.amount.valueInCents", equalTo("15075")))
                .withRequestBody(matchingJsonPath("$.transactionDate", containing("-06:00"))));
    }

    @Test
    void duplicado409_seTrataComoEntregado() {
        String cuerpo = "{\"status\":409,\"code\":\"DUPLICATE_PAYMENT\",\"message\":\"duplicado\","
                + "\"retryable\":false,\"originalReceipt\":" + RECIBO + "}";
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(409, cuerpo)));

        ResultadoEnvio resultado = servicio.enviar(mensaje("efectivo"));

        assertThat(resultado.exitoso()).isTrue();
        assertThat(resultado.receiptId()).isEqualTo("BNK-20261019-ABC123");
    }

    @Test
    void llaveReutilizada409_noSeReintenta() {
        String cuerpo = "{\"status\":409,\"code\":\"IDEMPOTENCY_KEY_REUSED\",\"message\":\"datos distintos\","
                + "\"retryable\":false}";
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(409, cuerpo)));

        ResultadoEnvio resultado = servicio.enviar(mensaje("efectivo"));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isFalse();
        assertThat(resultado.codigoError()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void error400_noSeReintenta() {
        String cuerpo = "{\"status\":400,\"code\":\"VALIDATION_ERROR\",\"message\":\"datos invalidos\","
                + "\"retryable\":false}";
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(400, cuerpo)));

        ResultadoEnvio resultado = servicio.enviar(mensaje("efectivo"));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(400);
        assertThat(resultado.codigoError()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void error403_sinApiKeyValida_noSeReintenta() {
        // API Gateway responde 403 con un cuerpo simple, sin el formato BankError
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(403, "{\"message\":\"Forbidden\"}")));

        ResultadoEnvio resultado = servicio.enviar(mensaje("efectivo"));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(403);
        assertThat(resultado.codigoError()).isNull();
    }

    @Test
    void error503_esReintentable() {
        String cuerpo = "{\"status\":503,\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"caido\",\"retryable\":true}";
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(503, cuerpo)));

        ResultadoEnvio resultado = servicio.enviar(mensaje("efectivo"));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isTrue();
        assertThat(resultado.httpStatus()).isEqualTo(503);
    }

    @Test
    void error422_noSeReintenta() {
        String cuerpo = "{\"status\":422,\"code\":\"AMOUNT_LIMIT_EXCEEDED\",\"message\":\"monto alto\","
                + "\"retryable\":false}";
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(422, cuerpo)));

        ResultadoEnvio resultado = servicio.enviar(mensaje("efectivo"));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isFalse();
        assertThat(resultado.codigoError()).isEqualTo("AMOUNT_LIMIT_EXCEEDED");
    }

    @ParameterizedTest
    @CsvSource({"EFECTIVO,CASH", "tarjeta,CARD", "transferencia,BANK_TRANSFER", "cheque,CHECK"})
    void laFormaDePagoSeMapeaAlCanalDelBanco(String formaPago, String canalEsperado) {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(201, RECIBO)));

        servicio.enviar(mensaje(formaPago));

        wireMock.verify(postRequestedFor(urlEqualTo(RUTA))
                .withRequestBody(matchingJsonPath("$.channel", equalTo(canalEsperado))));
    }

    @Test
    void cheque_elBancoLoRechazaCon422_yNoSeReintenta() {
        String cuerpo = "{\"status\":422,\"code\":\"UNSUPPORTED_CHANNEL\",\"message\":\"canal no soportado\","
                + "\"retryable\":false}";
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(422, cuerpo)));

        ResultadoEnvio resultado = servicio.enviar(mensaje("cheque"));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isFalse();
        assertThat(resultado.codigoError()).isEqualTo("UNSUPPORTED_CHANNEL");
        wireMock.verify(postRequestedFor(urlEqualTo(RUTA))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("CHECK"))));
    }

    @Test
    void formaDePagoDesconocida_fallaSinLlamarAlBanco() {
        ResultadoEnvio resultado = servicio.enviar(mensaje("bitcoin"));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.codigoError()).isEqualTo("MENSAJE_INVALIDO");
        wireMock.verify(0, postRequestedFor(urlEqualTo(RUTA)));
    }

    @Test
    void correlationId_seEnviaElDelMensaje() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(201, RECIBO)));

        servicio.enviar(mensaje("efectivo", "seguimiento-123"));

        wireMock.verify(postRequestedFor(urlEqualTo(RUTA))
                .withHeader("X-Correlation-Id", equalTo("seguimiento-123")));
    }

    @Test
    void correlationId_seGeneraSiElMensajeNoLoTrae() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(201, RECIBO)));

        servicio.enviar(mensaje("efectivo", null));

        wireMock.verify(postRequestedFor(urlEqualTo(RUTA))
                .withHeader("X-Correlation-Id", matching("[0-9a-f-]{36}")));
    }

    @Test
    void timeout_esReintentable() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(201, RECIBO).withFixedDelay(4500)));

        ResultadoEnvio resultado = servicio.enviar(mensaje("efectivo"));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isTrue();
        assertThat(resultado.httpStatus()).isNull();
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int estado, String cuerpo) {
        return aResponse().withStatus(estado).withHeader("Content-Type", "application/json").withBody(cuerpo);
    }

    private static MensajePago mensaje(String formaPago) {
        return mensaje(formaPago, null);
    }

    private static MensajePago mensaje(String formaPago, String correlationId) {
        return new MensajePago("EQA-000001", Destino.BANCO, "CLI-0001", "Ana Lopez", "CONT-1",
                2026, 9, 15075, 15075, 0, null, LocalDateTime.of(2026, 10, 19, 10, 30), formaPago,
                correlationId);
    }
    @Test
    void error503_conRetryAfter_conservaElCodigoDeError() {
        // Con Retry-After, Feign lanza RetryableException y el body queda en la causa
        String cuerpo = "{\"status\":503,\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"caido\",\"retryable\":true}";
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(503, cuerpo).withHeader("Retry-After", "5")));

        ResultadoEnvio resultado = servicio.enviar(mensaje("efectivo"));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isTrue();
        assertThat(resultado.httpStatus()).isEqualTo(503);
        assertThat(resultado.codigoError()).isEqualTo("SERVICE_UNAVAILABLE");
    }

}