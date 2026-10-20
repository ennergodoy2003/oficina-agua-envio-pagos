package oficinaagua.consumer.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import oficinaagua.consumer.pago.Destino;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Prueba de integracion (HU-46): escribe y lee en el DynamoDB Local del docker-compose.
 * Si DynamoDB Local no esta arriba, las pruebas se saltan en vez de fallar.
 */
class LogEnvioRepositoryTest {

    private static final String TABLA = "pagos-envio-logs";

    private static DynamoDbClient dynamo;
    private static LogEnvioRepository repo;

    /** Pagos inventados que creo cada prueba, para borrarlos al final. */
    private final List<String> creados = new ArrayList<>();

    @BeforeAll
    static void conectar() {
        dynamo = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:8000"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local", "local")))
                .build();
        Assumptions.assumeTrue(tablaDisponible(),
                "DynamoDB Local no esta arriba o falta la tabla: corre docker compose up -d");
        repo = new LogEnvioRepository(dynamo, TABLA);
    }

    @AfterAll
    static void cerrar() {
        if (dynamo != null) {
            dynamo.close();
        }
    }

    @AfterEach
    void limpiar() {
        for (String doc : creados) {
            for (RegistroIntento r : repo.historial(doc)) {
                dynamo.deleteItem(b -> b.tableName(TABLA).key(Map.of(
                        "numeroDocumento", AttributeValue.fromS(r.numeroDocumento()),
                        "claveIntento", AttributeValue.fromS(r.claveIntento()))));
            }
        }
    }

    @Test
    void guardaUnIntentoExitosoYLoDevuelveEnElHistorial() {
        String doc = docNuevo();
        repo.guardar(RegistroIntento.nuevo(doc, Destino.BANCO, true, 201, null,
                "Entregado al banco", "corr-123", "RCP-0001", null, null));

        List<RegistroIntento> historial = repo.historial(doc);

        assertThat(historial).hasSize(1);
        RegistroIntento r = historial.get(0);
        assertThat(r.numeroDocumento()).isEqualTo(doc);
        assertThat(r.claveIntento()).startsWith("BANCO#");
        assertThat(r.destino()).isEqualTo(Destino.BANCO);
        assertThat(r.resultado()).isEqualTo(RegistroIntento.EXITOSO);
        assertThat(r.httpStatus()).isEqualTo(201);
        assertThat(r.correlationId()).isEqualTo("corr-123");
        assertThat(r.receiptId()).isEqualTo("RCP-0001");
        assertThat(r.fecha()).isNotBlank();
    }

    @Test
    void unFalloSinRespuestaNoGuardaCamposVacios() {
        String doc = docNuevo();
        repo.guardar(RegistroIntento.nuevo(doc, Destino.BANCO, false, null, null,
                "Sin respuesta del banco (timeout o conexion)", "corr-456", null, null, null));

        RegistroIntento r = repo.historial(doc).get(0);

        assertThat(r.resultado()).isEqualTo(RegistroIntento.FALLIDO);
        assertThat(r.httpStatus()).isNull();
        assertThat(r.codigoError()).isNull();
        assertThat(r.receiptId()).isNull();
        assertThat(r.detalle()).contains("timeout");
    }

    @Test
    void cadaIntentoEsUnaLineaAgrupadaPorDestinoYEnOrden() throws InterruptedException {
        String doc = docNuevo();
        repo.guardar(RegistroIntento.nuevo(doc, Destino.ERP, true, 201, null,
                "Entregado al ERP", "corr-789", null, "CMP-0001", "PART-0001"));
        Thread.sleep(5);
        repo.guardar(RegistroIntento.nuevo(doc, Destino.BANCO, false, 503, "SERVICE_UNAVAILABLE",
                "El banco respondio 503", "corr-789", null, null, null));
        Thread.sleep(5);
        repo.guardar(RegistroIntento.nuevo(doc, Destino.BANCO, true, 201, null,
                "Entregado al banco", "corr-789", "RCP-0002", null, null));

        List<RegistroIntento> historial = repo.historial(doc);

        // BANCO va antes que ERP (orden de la claveIntento) y dentro de cada destino, por fecha
        assertThat(historial)
                .extracting(RegistroIntento::destino, RegistroIntento::resultado)
                .containsExactly(
                        tuple(Destino.BANCO, RegistroIntento.FALLIDO),
                        tuple(Destino.BANCO, RegistroIntento.EXITOSO),
                        tuple(Destino.ERP, RegistroIntento.EXITOSO));
        assertThat(historial.get(0).codigoError()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(historial.get(2).numeroComprobante()).isEqualTo("CMP-0001");
        assertThat(historial.get(2).numeroPartida()).isEqualTo("PART-0001");
    }

    @Test
    void unPagoSinIntentosDevuelveListaVacia() {
        assertThat(repo.historial(docNuevo())).isEmpty();
    }

    private static boolean tablaDisponible() {
        try {
            dynamo.describeTable(b -> b.tableName(TABLA));
            return true;
        } catch (SdkException e) {
            return false;
        }
    }

    @Test
    void yaEntregado_sinIntentos_esFalso() {
        assertThat(repo.yaEntregado(docNuevo(), Destino.BANCO)).isFalse();
    }

    @Test
    void yaEntregado_soloConFallos_esFalso() {
        String doc = docNuevo();
        repo.guardar(RegistroIntento.nuevo(doc, Destino.BANCO, false, 503, "SERVICE_UNAVAILABLE",
                "El banco respondio 503", "corr-1", null, null, null));

        assertThat(repo.yaEntregado(doc, Destino.BANCO)).isFalse();
    }

    @Test
    void yaEntregado_distingueElDestino() {
        String doc = docNuevo();
        repo.guardar(RegistroIntento.nuevo(doc, Destino.BANCO, true, 201, null,
                "Entregado al banco", "corr-2", "BNK-1", null, null));

        assertThat(repo.yaEntregado(doc, Destino.BANCO)).isTrue();
        assertThat(repo.yaEntregado(doc, Destino.ERP)).isFalse();
    }

    private String docNuevo() {
        String doc = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        creados.add(doc);
        return doc;
    }
}