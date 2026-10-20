package oficinaagua.consumer.banco;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Comprobante del banco (respuesta 201). Guardar receiptId para el registro. */
public record BankPaymentReceipt(
        String receiptId,
        String idempotencyKey,
        String teamCode,
        String status,
        Money amount,
        OffsetDateTime receivedAt,
        LocalDate valueDate) {
}