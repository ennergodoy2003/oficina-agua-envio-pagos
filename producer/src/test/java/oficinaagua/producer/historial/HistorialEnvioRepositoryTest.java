package oficinaagua.producer.historial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import oficinaagua.producer.pago.Destino;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * HU-46: traduccion de una linea de DynamoDB al contrato (schema IntentoEnvio).
 * Sin DynamoDB: el mapa imita lo que devuelve un Query.
 */
class HistorialEnvioRepositoryTest {

    @Test
    void exitoso_seTraduceAExito_sinMotivoFallo() {
        Map<String, AttributeValue> item = Map.of(
                "numeroDocumento", AttributeValue.fromS("EQA-000001"),
                "claveIntento", AttributeValue.fromS("BANCO#2026-10-20T18:12:30.883Z#a1b2c3d4"),
                "destino", AttributeValue.fromS("BANCO"),
                "resultado", AttributeValue.fromS("EXITOSO"),
                "fecha", AttributeValue.fromS("2026-10-20T18:12:30.883Z"),
                "httpStatus", AttributeValue.fromN("201"),
                "receiptId", AttributeValue.fromS("BNK-EQA-000001"),
                "detalle", AttributeValue.fromS("Entregado al banco"));

        IntentoEnvio intento = HistorialEnvioRepository.aIntento(item);

        // Aunque la linea trae "detalle", un exito NO lleva motivoFallo
        assertThat(intento).isEqualTo(new IntentoEnvio(
                "EQA-000001", Destino.BANCO, "EXITO", "2026-10-20T18:12:30.883Z", null));
    }

    @Test
    void fallido_seTraduceAFallo_conElDetalleComoMotivo() {
        Map<String, AttributeValue> item = Map.of(
                "numeroDocumento", AttributeValue.fromS("EQA-000003"),
                "claveIntento", AttributeValue.fromS("BANCO#2026-10-20T18:12:31.649Z#e5f6a7b8"),
                "destino", AttributeValue.fromS("BANCO"),
                "resultado", AttributeValue.fromS("FALLIDO"),
                "fecha", AttributeValue.fromS("2026-10-20T18:12:31.649Z"),
                "httpStatus", AttributeValue.fromN("503"),
                "codigoError", AttributeValue.fromS("SERVICE_UNAVAILABLE"),
                "detalle", AttributeValue.fromS("El banco respondio 503 SERVICE_UNAVAILABLE"));

        IntentoEnvio intento = HistorialEnvioRepository.aIntento(item);

        assertThat(intento).isEqualTo(new IntentoEnvio(
                "EQA-000003", Destino.BANCO, "FALLO", "2026-10-20T18:12:31.649Z",
                "El banco respondio 503 SERVICE_UNAVAILABLE"));
    }
}