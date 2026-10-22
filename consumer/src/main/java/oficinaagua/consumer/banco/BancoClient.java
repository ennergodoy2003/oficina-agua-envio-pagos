package oficinaagua.consumer.banco;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/** Ventanilla del banco. Contrato: docs/openapi/bank-payments-api.yaml (operationId sendBankPayment). */
@FeignClient(name = "banco", url = "${banco.url}", configuration = BancoFeignConfig.class)
public interface BancoClient {

    @PostMapping(value = "/bank/v1/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    BankPaymentReceipt enviarPago(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestBody BankPaymentRequest solicitud);
    /** HU-60: consulta si el banco ya registro el pago (contrato: getBankPayment). 404 si no existe. */
    @GetMapping("/bank/v1/payments/{idempotencyKey}")
    BankPaymentReceipt consultarPago(
            @PathVariable("idempotencyKey") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId);
}