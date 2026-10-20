package oficinaagua.consumer.banco;

import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;
import oficinaagua.consumer.pago.MensajePago;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Traduce el sobre de RabbitMQ al formato del banco. Sin estado mutable: es un Singleton. */
@Component
public class BancoMapper {

    private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");
    private static final Pattern LLAVE_VALIDA = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final int MAX_DESCRIPCION = 140;

    private final String teamCode;

    public BancoMapper(@Value("${banco.team-code}") String teamCode) {
        this.teamCode = teamCode;
    }

    /** La llave debe ser estable: siempre el numero de documento, nunca un UUID nuevo por intento. */
    public String llaveIdempotencia(MensajePago mensaje) {
        String llave = mensaje.numeroDocumento();
        if (llave == null || !LLAVE_VALIDA.matcher(llave).matches()) {
            throw new IllegalArgumentException(
                    "numeroDocumento no sirve como Idempotency-Key (8 a 64 caracteres: letras, numeros, _ y -): " + llave);
        }
        return llave;
    }

    public BankPaymentRequest aSolicitud(MensajePago mensaje) {
        return new BankPaymentRequest(
                teamCode,
                new Payer(mensaje.codigoCliente(), mensaje.nombreCliente()),
                new Money(mensaje.montoCentavos(), "GTQ"),
                // keko guarda hora local de Guatemala sin zona: se le pone -06:00, nunca Z
                mensaje.fechaPago().atZone(ZONA_GUATEMALA).toOffsetDateTime(),
                canal(mensaje.formaPago()),
                descripcion(mensaje));
    }

    private String canal(String formaPago) {
        String forma = formaPago == null ? "" : formaPago.trim().toLowerCase(Locale.ROOT);
        return switch (forma) {
            case "efectivo" -> "CASH";
            case "tarjeta" -> "CARD";
            case "transferencia" -> "BANK_TRANSFER";
            // El banco no acepta CHECK (422): va a dead-letter, como pide HU-44
            case "cheque" -> "CHECK";
            default -> throw new IllegalArgumentException(
                    "formaPago desconocida, no se puede mapear a un canal del banco: " + formaPago);
        };
    }

    private String descripcion(MensajePago mensaje) {
        String texto = String.format("Servicio de agua %04d-%02d", mensaje.periodoAnio(), mensaje.periodoMes());
        if (mensaje.conceptoAdicional() != null && !mensaje.conceptoAdicional().isBlank()) {
            texto += " + " + mensaje.conceptoAdicional().trim();
        }
        return texto.length() > MAX_DESCRIPCION ? texto.substring(0, MAX_DESCRIPCION) : texto;
    }
}