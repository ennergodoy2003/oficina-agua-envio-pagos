package oficinaagua.consumer.banco;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Error del banco. Integer/Boolean (no int/boolean) porque el API Gateway de AWS a veces
 * responde solo {"message":"Internal server error"}, sin estos campos. La decision de
 * reintentar se toma por el codigo HTTP, no por retryable.
 */
public record BankError(
        Integer status,
        String code,
        String message,
        Boolean retryable,
        String correlationId,
        OffsetDateTime timestamp,
        List<BankFieldError> details,
        BankPaymentReceipt originalReceipt) {
}