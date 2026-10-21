package oficinaagua.consumer.reintentos;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** HU-48: POST /api/v1/dlq/reprocesar?limite=20, para usar cuando el destino vuelve. */
@RestController
@RequestMapping("/api/v1/dlq")
public class DlqController {

    private final ReprocesadorDlq reprocesador;

    public DlqController(ReprocesadorDlq reprocesador) {
        this.reprocesador = reprocesador;
    }

    @PostMapping("/reprocesar")
    public ResultadoReproceso reprocesar(@RequestParam(defaultValue = "20") int limite) {
        if (limite < 1 || limite > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limite debe estar entre 1 y 100");
        }
        return reprocesador.reprocesar(limite);
    }
}