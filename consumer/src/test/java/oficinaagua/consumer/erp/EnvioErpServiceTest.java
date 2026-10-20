package oficinaagua.consumer.erp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.LocalDateTime;
import oficinaagua.consumer.pago.Destino;
import oficinaagua.consumer.pago.MensajePago;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
/** Prueba contra un ERP falso (WireMock). No toca RabbitMQ, el contenedor erp-falso ni el simulador del docente. */
@SpringBootTest(properties = {
        "spring.rabbitmq.username=test",
        "spring.rabbitmq.password=test",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "banco.api-key=clave-banco-prueba",
        "banco.team-code=EQUIPO_A",
        "erp.api-key=clave-erp-prueba",
        "erp.equipo=EQUIPO_A"
})
class EnvioErpServiceTest {

    private static final String RUTA = "/erp/v1/pagos";

    private static final String COMPROBANTE = """
            {"numero_comprobante":"ERP-20261020-Q7W2ZX","numero_documento_origen":"EQA-000001",
             "equipo":"EQUIPO_A","estado":"CONTABILIZADO","total":"150.75",
             "fecha_registro":"2026-10-20T18:00:00Z","periodo_contable":{"anio":2026,"mes":10},
             "numero_partida":"PDA-2026-000873"}""";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("erp.url", wireMock::baseUrl);
        registro.add("banco.url", wireMock::baseUrl); // no se usa aqui, pero Spring necesita un valor
    }

    @Autowired
    EnvioErpService servicio;

    @Test
    void respuesta201_esExito_yLaPeticionLlevaLoQueDiceElContrato() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(201, COMPROBANTE)));

        ResultadoEnvioErp resultado = servicio.enviar(mensaje("efectivo", "seguimiento-123"));

        assertThat(resultado.exitoso()).isTrue();
        assertThat(resultado.httpStatus()).isEqualTo(201);
        assertThat(resultado.numeroComprobante()).isEqualTo("ERP-20261020-Q7W2ZX");
        assertThat(resultado.numeroPartida()).isEqualTo("PDA-2026-000873");
        wireMock.verify(postRequestedFor(urlEqualTo(RUTA))
                .withHeader("x-api-key", equalTo("clave-erp-prueba"))
                .withHeader("X-Correlation-Id", equalTo("seguimiento-123"))
                .withoutHeader("Idempotency-Key")
                .withRequestBody(matchingJsonPath("$.equipo", equalTo("EQUIPO_A")))
                .withRequestBody(matchingJsonPath("$.documento_origen.numero", equalTo("EQA-000001")))
                .withRequestBody(matchingJsonPath("$.periodo_fiscal.anio", equalTo("2026")))
                .withRequestBody(matchingJsonPath("$.forma_pago", equalTo("EFECTIVO")))
                .withRequestBody(matchingJsonPath("$.total", equalTo("150.75")))
                .withRequestBody(matchingJsonPath("$.detalle[0].rubro", equalTo("AGUA_POTABLE")))
                .withRequestBody(matchingJsonPath("$.fecha_pago", containing("-06:00"))));
    }

    @Test
    void duplicado409_seTrataComoEntregado_conElComprobanteOriginal() {
        String cuerpo = "{\"estado_http\":409,\"codigo_error\":\"DOCUMENTO_DUPLICADO\","
                + "\"mensaje\":\"ya fue contabilizado\",\"reintentable\":false,"
                + "\"fecha_hora\":\"2026-10-20T18:00:05Z\",\"comprobante_original\":" + COMPROBANTE + "}";
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(409, cuerpo)));

        ResultadoEnvioErp resultado = servicio.enviar(mensaje("efectivo", null));

        assertThat(resultado.exitoso()).isTrue();
        assertThat(resultado.httpStatus()).isEqualTo(409);
        assertThat(resultado.numeroComprobante()).isEqualTo("ERP-20261020-Q7W2ZX");
        assertThat(resultado.numeroPartida()).isEqualTo("PDA-2026-000873");
    }

    @Test
    void reutilizado409_noSeReintenta() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(409, error(409, "DOCUMENTO_REUTILIZADO", false))));

        ResultadoEnvioErp resultado = servicio.enviar(mensaje("efectivo", null));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isFalse();
        assertThat(resultado.codigoError()).isEqualTo("DOCUMENTO_REUTILIZADO");
    }

    @Test
    void error400_noSeReintenta() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(400, error(400, "VALIDACION", false))));

        ResultadoEnvioErp resultado = servicio.enviar(mensaje("efectivo", null));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(400);
        assertThat(resultado.codigoError()).isEqualTo("VALIDACION");
    }

    @Test
    void error403_deApiGateway_noSeReintenta() {
        // API Gateway responde 403 con un cuerpo simple, sin el formato ErpError
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(403, "{\"message\":\"Forbidden\"}")));

        ResultadoEnvioErp resultado = servicio.enviar(mensaje("efectivo", null));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isFalse();
        assertThat(resultado.httpStatus()).isEqualTo(403);
        assertThat(resultado.codigoError()).isNull();
    }

    @Test
    void error422_noSeReintenta() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(422, error(422, "TOTAL_NO_CUADRA", false))));

        ResultadoEnvioErp resultado = servicio.enviar(mensaje("efectivo", null));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isFalse();
        assertThat(resultado.codigoError()).isEqualTo("TOTAL_NO_CUADRA");
    }

    @Test
    void error503_esReintentable() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(503, error(503, "SERVICIO_NO_DISPONIBLE", true))));

        ResultadoEnvioErp resultado = servicio.enviar(mensaje("efectivo", null));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isTrue();
        assertThat(resultado.httpStatus()).isEqualTo(503);
        assertThat(resultado.codigoError()).isEqualTo("SERVICIO_NO_DISPONIBLE");
    }

    @Test
    void timeout_esReintentable() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(201, COMPROBANTE).withFixedDelay(4500)));

        ResultadoEnvioErp resultado = servicio.enviar(mensaje("efectivo", null));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isTrue();
        assertThat(resultado.httpStatus()).isNull();
    }

    @Test
    void detalleQueNoCuadra_fallaSinLlamarAlErp() {
        // total 150.75 pero consumo 140.75 + adicional 5.00
        MensajePago descuadrado = new MensajePago("EQA-000001", Destino.ERP, "CLI-0001", "Ana Lopez",
                "MTR-00001", 2026, 9, 15075, 14075, 500, "Mora", LocalDateTime.of(2026, 10, 19, 10, 30),
                "efectivo", null);

        ResultadoEnvioErp resultado = servicio.enviar(descuadrado);

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.codigoError()).isEqualTo("MENSAJE_INVALIDO");
        wireMock.verify(0, postRequestedFor(urlEqualTo(RUTA)));
    }

    @Test
    void cheque_elErpSiLoAcepta() {
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(json(201, COMPROBANTE)));

        ResultadoEnvioErp resultado = servicio.enviar(mensaje("cheque", null));

        assertThat(resultado.exitoso()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo(RUTA))
                .withRequestBody(matchingJsonPath("$.forma_pago", equalTo("CHEQUE"))));
    }

    private static String error(int estado, String codigo, boolean reintentable) {
        return "{\"estado_http\":" + estado + ",\"codigo_error\":\"" + codigo + "\",\"mensaje\":\"prueba\","
                + "\"reintentable\":" + reintentable + ",\"fecha_hora\":\"2026-10-20T18:00:00Z\"}";
    }

    private static ResponseDefinitionBuilder json(int estado, String cuerpo) {
        return aResponse().withStatus(estado).withHeader("Content-Type", "application/json").withBody(cuerpo);
    }

    private static MensajePago mensaje(String formaPago, String correlationId) {
        return new MensajePago("EQA-000001", Destino.ERP, "CLI-0001", "Ana Lopez", "MTR-00001",
                2026, 9, 15075, 15075, 0, null, LocalDateTime.of(2026, 10, 19, 10, 30), formaPago,
                correlationId);
    }

    @Test
    void error503_conRetryAfter_conservaElCodigoDeError() {
        // Con Retry-After, Feign lanza RetryableException y el body queda en la causa
        wireMock.stubFor(post(urlEqualTo(RUTA)).willReturn(
                json(503, error(503, "SERVICIO_NO_DISPONIBLE", true)).withHeader("Retry-After", "5")));

        ResultadoEnvioErp resultado = servicio.enviar(mensaje("efectivo", null));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.reintentable()).isTrue();
        assertThat(resultado.httpStatus()).isEqualTo(503);
        assertThat(resultado.codigoError()).isEqualTo("SERVICIO_NO_DISPONIBLE");
    }
    @Test
    void lostAck_laReentregaRecibe409_yNoHaySegundoRegistro() {
        String duplicado = "{\"estado_http\":409,\"codigo_error\":\"DOCUMENTO_DUPLICADO\","
                + "\"mensaje\":\"ya fue contabilizado\",\"reintentable\":false,"
                + "\"fecha_hora\":\"2026-10-20T18:00:05Z\",\"comprobante_original\":" + COMPROBANTE + "}";
        // 1er envio: el ERP registra pero la respuesta llega tarde (se pierde el ack)
        wireMock.stubFor(post(urlEqualTo(RUTA)).inScenario("LOST_ACK")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(json(201, COMPROBANTE).withFixedDelay(4500))
                .willSetStateTo("REGISTRADO"));
        // Reentrega: el ERP ya lo tiene
        wireMock.stubFor(post(urlEqualTo(RUTA)).inScenario("LOST_ACK")
                .whenScenarioStateIs("REGISTRADO")
                .willReturn(json(409, duplicado)));

        ResultadoEnvioErp primero = servicio.enviar(mensaje("efectivo", null));
        assertThat(primero.exitoso()).isFalse();
        assertThat(primero.reintentable()).isTrue();

        ResultadoEnvioErp reentrega = servicio.enviar(mensaje("efectivo", null));
        assertThat(reentrega.exitoso()).isTrue();
        assertThat(reentrega.httpStatus()).isEqualTo(409);
        assertThat(reentrega.numeroComprobante()).isEqualTo("ERP-20261020-Q7W2ZX");

        // Las dos peticiones con el MISMO documento: por eso el ERP detecta el duplicado
        wireMock.verify(2, postRequestedFor(urlEqualTo(RUTA))
                .withRequestBody(matchingJsonPath("$.documento_origen.numero", equalTo("EQA-000001"))));
    }


}