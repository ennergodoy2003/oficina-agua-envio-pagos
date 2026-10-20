package oficinaagua.consumer.erp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Body de POST /erp/v1/pagos. Contrato: docs/openapi/erp-payments-api.yaml (schema ErpRegistroPago).
 * En Java camelCase; en el JSON snake_case (documento_origen, periodo_fiscal...).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErpRegistroPago(
        String equipo,
        ErpDocumentoOrigen documentoOrigen,
        ErpContribuyente contribuyente,
        ErpPeriodo periodoFiscal,
        String formaPago,
        OffsetDateTime fechaPago,
        String total,
        List<ErpLineaDetalle> detalle,
        String observaciones) {
}