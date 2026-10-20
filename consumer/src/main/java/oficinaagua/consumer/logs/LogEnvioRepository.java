package oficinaagua.consumer.logs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oficinaagua.consumer.pago.Destino;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

/** Libro de registro de envios en DynamoDB (HU-46). Una linea por intento. */
@Repository
public class LogEnvioRepository {

    private final DynamoDbClient dynamo;
    private final String tabla;

    public LogEnvioRepository(DynamoDbClient dynamo, @Value("${dynamodb.table}") String tabla) {
        this.dynamo = dynamo;
        this.tabla = tabla;
    }

    /** Escribe un intento. Solo guarda los atributos que traen valor. */
    public void guardar(RegistroIntento r) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("numeroDocumento", AttributeValue.fromS(r.numeroDocumento()));
        item.put("claveIntento", AttributeValue.fromS(r.claveIntento()));
        item.put("destino", AttributeValue.fromS(r.destino().name()));
        item.put("resultado", AttributeValue.fromS(r.resultado()));
        item.put("fecha", AttributeValue.fromS(r.fecha()));
        if (r.httpStatus() != null) {
            item.put("httpStatus", AttributeValue.fromN(String.valueOf(r.httpStatus())));
        }
        ponerTexto(item, "codigoError", r.codigoError());
        ponerTexto(item, "detalle", r.detalle());
        ponerTexto(item, "correlationId", r.correlationId());
        ponerTexto(item, "receiptId", r.receiptId());
        ponerTexto(item, "numeroComprobante", r.numeroComprobante());
        ponerTexto(item, "numeroPartida", r.numeroPartida());

        dynamo.putItem(PutItemRequest.builder()
                .tableName(tabla)
                .item(item)
                .build());
    }

    /** Todos los intentos de un pago, agrupados por destino y en orden cronologico. */
    public List<RegistroIntento> historial(String numeroDocumento) {
        QueryRequest consulta = QueryRequest.builder()
                .tableName(tabla)
                .keyConditionExpression("numeroDocumento = :doc")
                .expressionAttributeValues(Map.of(":doc", AttributeValue.fromS(numeroDocumento)))
                .build();
        return dynamo.query(consulta).items().stream()
                .map(this::aRegistro)
                .toList();
    }

    /** HU-47: ¿este pago ya tiene un intento EXITOSO en este destino? */
    public boolean yaEntregado(String numeroDocumento, Destino destino) {
        QueryRequest consulta = QueryRequest.builder()
                .tableName(tabla)
                .keyConditionExpression("numeroDocumento = :doc AND begins_with(claveIntento, :prefijo)")
                .filterExpression("resultado = :exitoso")
                .expressionAttributeValues(Map.of(
                        ":doc", AttributeValue.fromS(numeroDocumento),
                        ":prefijo", AttributeValue.fromS(destino.name() + "#"),
                        ":exitoso", AttributeValue.fromS(RegistroIntento.EXITOSO)))
                .build();
        return dynamo.query(consulta).count() > 0;
    }

    private static void ponerTexto(Map<String, AttributeValue> item, String nombre, String valor) {
        if (valor != null && !valor.isBlank()) {
            item.put(nombre, AttributeValue.fromS(valor));
        }
    }

    private RegistroIntento aRegistro(Map<String, AttributeValue> item) {
        AttributeValue status = item.get("httpStatus");
        return new RegistroIntento(
                texto(item, "numeroDocumento"),
                texto(item, "claveIntento"),
                Destino.valueOf(texto(item, "destino")),
                texto(item, "resultado"),
                texto(item, "fecha"),
                status != null ? Integer.valueOf(status.n()) : null,
                texto(item, "codigoError"),
                texto(item, "detalle"),
                texto(item, "correlationId"),
                texto(item, "receiptId"),
                texto(item, "numeroComprobante"),
                texto(item, "numeroPartida"));
    }

    private static String texto(Map<String, AttributeValue> item, String nombre) {
        AttributeValue valor = item.get(nombre);
        return valor != null ? valor.s() : null;
    }
}