package oficinaagua.consumer.erp;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;

/** Respuesta 201 del ERP. Guardar numeroComprobante y numeroPartida en el registro (HU-46). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ErpComprobante(
        String numeroComprobante,
        String numeroDocumentoOrigen,
        String equipo,
        String estado,
        String total,
        OffsetDateTime fechaRegistro,
        ErpPeriodo periodoContable,
        String numeroPartida) {
}