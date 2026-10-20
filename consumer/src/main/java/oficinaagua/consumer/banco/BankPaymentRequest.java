package oficinaagua.consumer.banco;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** Cuerpo del POST /bank/v1/payments. La llave de unicidad NO va aqui: va en el header Idempotency-Key. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BankPaymentRequest(
        String teamCode,
        Payer payer,
        Money amount,
        OffsetDateTime transactionDate,
        String channel,
        String description) {
}