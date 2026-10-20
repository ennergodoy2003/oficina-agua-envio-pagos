package oficinaagua.producer.historial;

/** El pagoId no existe en la base de pagos: el endpoint responde 404. */
public class PagoNoEncontradoException extends RuntimeException {

    public PagoNoEncontradoException(String pagoId) {
        super("No existe el pago " + pagoId);
    }
}