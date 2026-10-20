package oficinaagua.consumer.erp;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Una linea por rubro. La suma de los montos debe ser igual al total (si no: 422 TOTAL_NO_CUADRA). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErpLineaDetalle(
        String rubro,
        String descripcion,
        String monto) {
}