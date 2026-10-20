package oficinaagua.producer.historial;

import java.util.List;
import oficinaagua.producer.pago.PagoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contrato: GET /api/v1/pagos/{pagoId}/envios (operationId consultarEnviosDePago). */
@RestController
@RequestMapping("/api/v1/pagos")
public class HistorialController {

    private final PagoRepository pagos;
    private final HistorialEnvioRepository historial;

    public HistorialController(PagoRepository pagos, HistorialEnvioRepository historial) {
        this.pagos = pagos;
        this.historial = historial;
    }

    @GetMapping("/{pagoId}/envios")
    public List<IntentoEnvio> consultarEnviosDePago(@PathVariable String pagoId) {
        if (!pagos.existsByNumeroDocumento(pagoId)) {
            throw new PagoNoEncontradoException(pagoId);
        }
        return historial.intentosDe(pagoId);
    }
}