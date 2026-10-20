package oficinaagua.consumer.reintentos;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReintentosConfig {

    /** El Esperador real: duerme el hilo del listener. */
    @Bean
    public Esperador esperador() {
        return ms -> {
            try {
                Thread.sleep(ms);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        };
    }
}