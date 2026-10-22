package oficinaagua.consumer.erp;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Ventanilla del ERP. Contrato: docs/openapi/erp-payments-api.yaml (operationId registrarPagoErp).
 * Sin Idempotency-Key: la unicidad viaja en el body (documento_origen.numero).
 */
@FeignClient(name = "erp", url = "${erp.url}", configuration = ErpFeignConfig.class)
public interface ErpClient {

    @PostMapping(value = "/erp/v1/pagos", consumes = MediaType.APPLICATION_JSON_VALUE)
    ErpComprobante registrarPago(
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestBody ErpRegistroPago registro);
    /** HU-60: consulta si el ERP ya registro el documento (contrato: consultarPagoErp). 404 si no existe. */
    @GetMapping("/erp/v1/pagos/{numeroDocumento}")
    ErpComprobante consultarPago(
            @PathVariable("numeroDocumento") String numeroDocumento,
            @RequestHeader("X-Correlation-Id") String correlationId);
}