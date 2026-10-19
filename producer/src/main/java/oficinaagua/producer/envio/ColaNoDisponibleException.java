package oficinaagua.producer.envio;

public class ColaNoDisponibleException extends RuntimeException {

    public ColaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}