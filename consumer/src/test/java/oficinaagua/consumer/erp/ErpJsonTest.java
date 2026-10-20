package oficinaagua.consumer.erp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Los DTOs escriben y leen el JSON exactamente como dice el contrato del ERP (snake_case). */
class ErpJsonTest {

    /** Igual que el de Spring Boot: fechas como texto ISO, no como numeros. */
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void laPeticionSaleEnSnakeCase_comoElEjemploDelContrato() {
        ErpRegistroPago registro = new ErpRegistroPago(
                "EQUIPO_A",
                new ErpDocumentoOrigen("RECIBO_CAJA", "EQA-000123", LocalDate.of(2026, 10, 1)),
                new ErpContribuyente("CLI-0001", "MTR-00001", "CF"),
                new ErpPeriodo(2026, 9),
                "EFECTIVO",
                OffsetDateTime.parse("2026-10-01T10:15:00-06:00"),
                "150.75",
                List.of(new ErpLineaDetalle("AGUA_POTABLE", "Consumo septiembre 2026", "140.75"),
                        new ErpLineaDetalle("MORA", null, "10.00")),
                null);

        JsonNode json = mapper.valueToTree(registro);

        assertThat(json.get("documento_origen").get("numero").asText()).isEqualTo("EQA-000123");
        assertThat(json.get("documento_origen").get("fecha_emision").asText()).isEqualTo("2026-10-01");
        assertThat(json.get("contribuyente").get("codigo_cliente").asText()).isEqualTo("CLI-0001");
        assertThat(json.get("periodo_fiscal").get("mes").asInt()).isEqualTo(9);
        assertThat(json.get("forma_pago").asText()).isEqualTo("EFECTIVO");
        assertThat(json.get("fecha_pago").asText()).isEqualTo("2026-10-01T10:15:00-06:00");
        assertThat(json.get("total").asText()).isEqualTo("150.75");
        // Los null no se envian
        assertThat(json.has("observaciones")).isFalse();
        assertThat(json.get("detalle").get(1).has("descripcion")).isFalse();
    }

    @Test
    void elErrorDuplicadoSeLeeConSuComprobanteOriginal() throws Exception {
        // Ejemplo "duplicado" copiado del contrato
        String cuerpo = """
                {"estado_http":409,"codigo_error":"DOCUMENTO_DUPLICADO",
                 "mensaje":"El documento EQA-000123 ya fue contabilizado","reintentable":false,
                 "id_correlacion":"0a9b8c7d-6e5f-4a3b-2c1d-000000000001","fecha_hora":"2026-10-07T23:15:05Z",
                 "comprobante_original":{"numero_comprobante":"ERP-20261007-Q7W2ZX",
                   "numero_documento_origen":"EQA-000123","equipo":"EQUIPO_A","estado":"CONTABILIZADO",
                   "total":"150.75","fecha_registro":"2026-10-07T23:14:58Z",
                   "periodo_contable":{"anio":2026,"mes":10},"numero_partida":"PDA-2026-000873"}}
                """;

        ErpError error = mapper.readValue(cuerpo, ErpError.class);

        assertThat(error.codigoError()).isEqualTo("DOCUMENTO_DUPLICADO");
        assertThat(error.comprobanteOriginal().numeroComprobante()).isEqualTo("ERP-20261007-Q7W2ZX");
        assertThat(error.comprobanteOriginal().numeroPartida()).isEqualTo("PDA-2026-000873");
    }
}