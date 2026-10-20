package oficinaagua.consumer.erp;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Formato de error propio del ERP. codigoError decide que hacer (no parsear mensaje).
 * comprobanteOriginal solo viene en 409 DOCUMENTO_DUPLICADO.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ErpError(
        Integer estadoHttp,
        String codigoError,
        String mensaje,
        Boolean reintentable,
        String idCorrelacion,
        OffsetDateTime fechaHora,
        List<ErpErrorCampo> errores,
        ErpComprobante comprobanteOriginal) {
}