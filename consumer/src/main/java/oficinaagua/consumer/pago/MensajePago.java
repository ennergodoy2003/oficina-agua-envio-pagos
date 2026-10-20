package oficinaagua.consumer.pago;

import java.time.LocalDateTime;

/**
 * Sobre que llega por RabbitMQ. Es una copia propia del consumer: el producer y el consumer
 * no comparten clases, solo el contrato JSON. Los campos deben llamarse igual que en el producer.
 * correlationId: numero de seguimiento del sobre. Hasta que el producer lo mande (HU-43) llega null.
 */
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
        String formaPago,
        String correlationId) {
}