package oficinaagua.consumer.banco;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Ventanilla del banco. Contrato: docs/openapi/bank-payments-api.yaml (operationId sendBankPayment). */
@FeignClient(name = "banco", url = "${banco.url}", configuration = BancoFeignConfig.class)
public interface BancoClient {

    @PostMapping(value = "/bank/v1/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    BankPaymentReceipt enviarPago(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestBody BankPaymentRequest solicitud);
}