package oficinaagua.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestTemplate;
import oficinaagua.consumer.banco.BancoFeignConfig;
import oficinaagua.consumer.erp.ErpFeignConfig;
import org.junit.jupiter.api.Test;

/** HU-61: el header X-Simulation-Scenario solo se manda si el escenario tiene valor. */
class EscenarioSimulacionTest {

    @Test
    void conEscenario_elBancoMandaElHeader() {
        RequestTemplate peticion = new RequestTemplate();

        new BancoFeignConfig().bancoEscenarioInterceptor("ERROR_503").apply(peticion);

        assertThat(peticion.headers().get("X-Simulation-Scenario")).containsExactly("ERROR_503");
    }

    @Test
    void conEscenario_elErpMandaElHeader() {
        RequestTemplate peticion = new RequestTemplate();

        new ErpFeignConfig().erpEscenarioInterceptor("SLOW").apply(peticion);

        assertThat(peticion.headers().get("X-Simulation-Scenario")).containsExactly("SLOW");
    }

    @Test
    void sinEscenario_noSeMandaElHeader_yElSimuladorUsaAuto() {
        RequestTemplate peticion = new RequestTemplate();

        new BancoFeignConfig().bancoEscenarioInterceptor("").apply(peticion);
        new ErpFeignConfig().erpEscenarioInterceptor("  ").apply(peticion);

        assertThat(peticion.headers()).doesNotContainKey("X-Simulation-Scenario");
    }
}