package oficinaagua.consumer.erp;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

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
}