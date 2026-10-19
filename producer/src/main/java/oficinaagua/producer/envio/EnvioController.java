package oficinaagua.producer.envio;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/envios")
public class EnvioController {

    private static final int LIMITE_POR_DEFECTO = 20;
    private static final int LIMITE_MINIMO = 1;
    private static final int LIMITE_MAXIMO = 100;

    private final EnvioService envioService;

    public EnvioController(EnvioService envioService) {
        this.envioService = envioService;
    }

    @PostMapping
    public DisparoEnvioResponse dispararEnvio(
            @RequestBody(required = false) DisparoEnvioRequest request) {
        int limite = (request == null || request.limite() == null)
                ? LIMITE_POR_DEFECTO
                : request.limite();
        if (limite < LIMITE_MINIMO || limite > LIMITE_MAXIMO) {
            throw new LimiteInvalidoException(
                    "limite debe estar entre " + LIMITE_MINIMO + " y " + LIMITE_MAXIMO);
        }
        return envioService.disparar(limite);
    }
}