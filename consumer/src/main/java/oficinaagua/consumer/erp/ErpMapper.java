package oficinaagua.consumer.erp;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import oficinaagua.consumer.pago.MensajePago;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Traduce el sobre de RabbitMQ al formato del ERP. Sin estado mutable: es un Singleton. */
@Component
public class ErpMapper {

    private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");
    private static final Pattern NUMERO_VALIDO = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final int MAX_DESCRIPCION = 120;

    private final String equipo;

    public ErpMapper(@Value("${erp.equipo}") String equipo) {
        this.equipo = equipo;
    }

    /** La llave de unicidad del ERP: siempre el numero de documento, nunca uno nuevo por intento. */
    public String numeroDocumento(MensajePago mensaje) {
        String numero = mensaje.numeroDocumento();
        if (numero == null || !NUMERO_VALIDO.matcher(numero).matches()) {
            throw new IllegalArgumentException(
                    "numeroDocumento no sirve como documento_origen.numero (1 a 40: letras, numeros, _ y -): " + numero);
        }
        return numero;
    }

    public ErpRegistroPago aRegistro(MensajePago mensaje) {
        String numero = numeroDocumento(mensaje);
        if (mensaje.codigoCliente() == null || mensaje.codigoCliente().isBlank()) {
            throw new IllegalArgumentException("codigoCliente es obligatorio para el ERP");
        }
        return new ErpRegistroPago(
                equipo,
                new ErpDocumentoOrigen("RECIBO_CAJA", numero, mensaje.fechaPago().toLocalDate()),
                new ErpContribuyente(mensaje.codigoCliente(), vacioANull(mensaje.codigoContador()), null),
                new ErpPeriodo(mensaje.periodoAnio(), mensaje.periodoMes()),
                formaPago(mensaje.formaPago()),
                // keko guarda hora local de Guatemala sin zona: se le pone -06:00, nunca Z
                mensaje.fechaPago().atZone(ZONA_GUATEMALA).toOffsetDateTime(),
                monto(mensaje.montoCentavos()),
                detalle(mensaje),
                null);
    }

    /** Centavos a texto con 2 decimales exactos, sin double: 15075 -> "150.75". */
    static String monto(long centavos) {
        if (centavos < 0) {
            throw new IllegalArgumentException("monto negativo: " + centavos);
        }
        return BigDecimal.valueOf(centavos, 2).toPlainString();
    }

    /** Una linea por rubro. Si no cuadra con el total, ni se llama al ERP (evita 422 TOTAL_NO_CUADRA). */
    private List<ErpLineaDetalle> detalle(MensajePago mensaje) {
        long consumo = mensaje.consumoCentavos();
        long adicional = mensaje.adicionalCentavos();
        if (consumo + adicional != mensaje.montoCentavos()) {
            throw new IllegalArgumentException(String.format(
                    "el detalle no cuadra: consumo %s + adicional %s != total %s",
                    monto(consumo), monto(adicional), monto(mensaje.montoCentavos())));
        }

        List<ErpLineaDetalle> lineas = new ArrayList<>();
        lineas.add(new ErpLineaDetalle("AGUA_POTABLE",
                String.format("Consumo de agua %04d-%02d", mensaje.periodoAnio(), mensaje.periodoMes()),
                monto(consumo)));
        if (adicional > 0) {
            String concepto = mensaje.conceptoAdicional() == null ? "" : mensaje.conceptoAdicional().trim();
            String descripcion = concepto.isEmpty() ? "Cargo adicional" : recortar(concepto);
            lineas.add(new ErpLineaDetalle(rubro(concepto), descripcion, monto(adicional)));
        }
        return lineas;
    }

    /** reconex va antes que conexi porque "reconexion" contiene las dos. Sin tilde para aceptar ambas. */
    static String rubro(String concepto) {
        String texto = concepto.toLowerCase(Locale.ROOT);
        if (texto.contains("mora")) {
            return "MORA";
        }
        if (texto.contains("reconex")) {
            return "RECONEXION";
        }
        if (texto.contains("conexi") || texto.contains("instalaci")) {
            return "NUEVA_CONEXION";
        }
        return "OTROS";
    }

    private String formaPago(String formaPago) {
        String forma = formaPago == null ? "" : formaPago.trim().toLowerCase(Locale.ROOT);
        return switch (forma) {
            case "efectivo" -> "EFECTIVO";
            case "tarjeta" -> "TARJETA";
            case "transferencia" -> "TRANSFERENCIA";
            case "cheque" -> "CHEQUE";
            default -> throw new IllegalArgumentException(
                    "formaPago desconocida, no se puede mapear a forma_pago del ERP: " + formaPago);
        };
    }

    private static String recortar(String texto) {
        return texto.length() > MAX_DESCRIPCION ? texto.substring(0, MAX_DESCRIPCION) : texto;
    }

    private static String vacioANull(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto;
    }
}