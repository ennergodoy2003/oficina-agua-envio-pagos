package oficinaagua.producer.historial;

import java.util.List;
import java.util.Map;
import oficinaagua.producer.pago.Destino;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

/**
 * Lee el libro de registro que escribe el consumer (HU-46) y lo traduce al contrato.
 * Solo lectura: el producer nunca escribe en DynamoDB.
 */
@Repository
public class HistorialEnvioRepository {

    /** Valor interno que guarda el consumer; hacia afuera se traduce a EXITO / FALLO. */
    private static final String EXITOSO = "EXITOSO";

    private final DynamoDbClient dynamo;
    private final String tabla;

    public HistorialEnvioRepository(DynamoDbClient dynamo, @Value("${dynamodb.table}") String tabla) {
        this.dynamo = dynamo;
        this.tabla = tabla;
    }

    /** Todos los intentos de un pago, agrupados por destino y en orden cronologico. */
    public List<IntentoEnvio> intentosDe(String pagoId) {
        QueryRequest consulta = QueryRequest.builder()
                .tableName(tabla)
                .keyConditionExpression("numeroDocumento = :doc")
                .expressionAttributeValues(Map.of(":doc", AttributeValue.fromS(pagoId)))
                .build();
        return dynamo.query(consulta).items().stream()
                .map(HistorialEnvioRepository::aIntento)
                .toList();
    }

    /** Traduce una linea de DynamoDB al formato del contrato. */
    static IntentoEnvio aIntento(Map<String, AttributeValue> item) {
        boolean exito = EXITOSO.equals(texto(item, "resultado"));
        return new IntentoEnvio(
                texto(item, "numeroDocumento"),
                Destino.valueOf(texto(item, "destino")),
                exito ? IntentoEnvio.EXITO : IntentoEnvio.FALLO,
                texto(item, "fecha"),
                exito ? null : texto(item, "detalle"));
    }

    private static String texto(Map<String, AttributeValue> item, String nombre) {
        AttributeValue valor = item.get(nombre);
        return valor != null ? valor.s() : null;
    }
}