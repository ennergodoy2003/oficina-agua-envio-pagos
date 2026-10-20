package oficinaagua.consumer.banco;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Quien pago. El NIT no se envia (indicacion del proyecto), por eso taxId queda fuera. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Payer(String customerCode, String fullName) {
}