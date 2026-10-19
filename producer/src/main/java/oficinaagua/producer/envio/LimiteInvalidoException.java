package oficinaagua.producer.envio;

public class LimiteInvalidoException extends RuntimeException {

    public LimiteInvalidoException(String mensaje) {
        super(mensaje);
    }
}