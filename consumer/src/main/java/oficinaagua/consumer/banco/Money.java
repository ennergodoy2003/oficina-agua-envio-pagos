package oficinaagua.consumer.banco;

/** Monto en la unidad minima (centavos). Q150.75 = 15075. */
public record Money(long valueInCents, String currency) {
}