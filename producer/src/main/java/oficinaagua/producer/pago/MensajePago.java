package oficinaagua.producer.pago;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Sobre que viaja por RabbitMQ: lleva todo lo que el consumer necesita para armar la llamada. */
public record MensajePago(
        String numeroDocumento,
        Destino destino,
        String codigoCliente,
        String nombreCliente,
        String codigoContador,
        int periodoAnio,
        int periodoMes,
        long montoCentavos,
        long consumoCentavos,
        long adicionalCentavos,
        String conceptoAdicional,
        LocalDateTime fechaPago,
        String formaPago) {

    /** Un mensaje por cada destino en el que el pago sigue pendiente. */
    public static List<MensajePago> pendientesDe(Pago pago) {
        List<MensajePago> mensajes = new ArrayList<>();
        if (pago.getEstadoEnvioBanco() == EstadoEnvio.PENDIENTE) {
            mensajes.add(desde(pago, Destino.BANCO));
        }
        if (pago.getEstadoEnvioErp() == EstadoEnvio.PENDIENTE) {
            mensajes.add(desde(pago, Destino.ERP));
        }
        return mensajes;
    }

    private static MensajePago desde(Pago pago, Destino destino) {
        return new MensajePago(
                pago.getNumeroDocumento(),
                destino,
                pago.getCodigoCliente(),
                pago.getNombreCliente(),
                pago.getCodigoContador(),
                pago.getPeriodoAnio(),
                pago.getPeriodoMes(),
                pago.getMontoCentavos(),
                pago.getConsumoCentavos(),
                pago.getAdicionalCentavos(),
                pago.getConceptoAdicional(),
                pago.getFechaPago(),
                pago.getFormaPago());
    }
}