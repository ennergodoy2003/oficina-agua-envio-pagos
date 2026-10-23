package oficinaagua.consumer.erp;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/** Sin @Configuration a proposito: solo aplica al cliente que la referencia (ErpClient). */
public class ErpFeignConfig {

    @Bean
    public RequestInterceptor erpApiKeyInterceptor(@Value("${erp.api-key}") String apiKey) {
        return template -> template.header("x-api-key", apiKey);
    }
    /** HU-61: si SIMULACION_ESCENARIO tiene valor, se manda X-Simulation-Scenario; vacio = AUTO. */
    @Bean
    public RequestInterceptor erpEscenarioInterceptor(@Value("${simulador.escenario:}") String escenario) {
        return template -> {
            if (escenario != null && !escenario.isBlank()) {
                template.header("X-Simulation-Scenario", escenario.trim());
            }
        };
    }
}