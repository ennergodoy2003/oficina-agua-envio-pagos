package oficinaagua.consumer.erp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;

/** numero es la llave de unicidad del ERP: la llave estable del pago (EQA-000123). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErpDocumentoOrigen(
        String tipo,
        String numero,
        LocalDate fechaEmision) {
}