package oficinaagua.producer.envio;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(LimiteInvalidoException.class)
    public ResponseEntity<ErrorResponse> limiteInvalido(LimiteInvalidoException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("LIMITE_INVALIDO", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("CUERPO_INVALIDO", "El cuerpo no es un JSON valido"));
    }

    @ExceptionHandler(ColaNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> colaNoDisponible(ColaNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("COLA_NO_DISPONIBLE",
                        "RabbitMQ no responde. Los pagos siguen pendientes."));
    }
}