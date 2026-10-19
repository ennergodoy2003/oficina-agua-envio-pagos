package oficinaagua.producer.pago;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/** Prueba de integración: necesita MariaDB con la base keko_copia y el .env configurado. */
@SpringBootTest
class PagoRepositoryTest {

    @Autowired
    PagoRepository repository;

    @Test
    void devuelveSoloPagosPendientesEnAlgunDestino() {
        List<Pago> pendientes = repository.buscarPendientes(EstadoEnvio.PENDIENTE, PageRequest.of(0, 50));

        assertThat(pendientes).isNotEmpty().allSatisfy(p ->
                assertThat(p.getEstadoEnvioBanco() == EstadoEnvio.PENDIENTE
                        || p.getEstadoEnvioErp() == EstadoEnvio.PENDIENTE).isTrue());
    }

    @Test
    void respetaElLimiteDelLote() {
        List<Pago> lote = repository.buscarPendientes(EstadoEnvio.PENDIENTE, PageRequest.of(0, 2));

        assertThat(lote).hasSize(2);
    }
}