package oficinaagua.producer.pago;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MensajePagoTest {

    @Test
    void pendienteEnAmbosDestinosGeneraDosMensajes() {
        Pago pago = pago("EQA-000001", EstadoEnvio.PENDIENTE, EstadoEnvio.PENDIENTE);

        List<MensajePago> mensajes = MensajePago.pendientesDe(pago);

        assertThat(mensajes).extracting(MensajePago::destino)
                .containsExactly(Destino.BANCO, Destino.ERP);
        assertThat(mensajes).allSatisfy(m -> {
            assertThat(m.numeroDocumento()).isEqualTo("EQA-000001");
            assertThat(m.codigoCliente()).isEqualTo("CLI-00001");
            assertThat(m.periodoAnio()).isEqualTo(2026);
            assertThat(m.periodoMes()).isEqualTo(10);
            assertThat(m.montoCentavos()).isEqualTo(15075L);
            assertThat(m.consumoCentavos() + m.adicionalCentavos()).isEqualTo(15075L);
        });
    }

    @Test
    void pendienteSoloEnErpGeneraUnMensaje() {
        Pago pago = pago("EQA-000003", EstadoEnvio.ENVIADO, EstadoEnvio.PENDIENTE);

        assertThat(MensajePago.pendientesDe(pago)).extracting(MensajePago::destino)
                .containsExactly(Destino.ERP);
    }

    @Test
    void pendienteSoloEnBancoGeneraUnMensaje() {
        Pago pago = pago("EQA-000005", EstadoEnvio.PENDIENTE, EstadoEnvio.ENVIADO);

        assertThat(MensajePago.pendientesDe(pago)).extracting(MensajePago::destino)
                .containsExactly(Destino.BANCO);
    }

    @Test
    void pagoYaEnviadoNoGeneraMensajes() {
        Pago pago = pago("EQA-000004", EstadoEnvio.ENVIADO, EstadoEnvio.ENVIADO);

        assertThat(MensajePago.pendientesDe(pago)).isEmpty();
    }

    @Test
    void cadaMensajeLlevaSuPropioCorrelationId() {
        Pago pago = pago("EQA-000001", EstadoEnvio.PENDIENTE, EstadoEnvio.PENDIENTE);

        List<MensajePago> mensajes = MensajePago.pendientesDe(pago);

        assertThat(mensajes).extracting(MensajePago::correlationId)
                .allSatisfy(id -> assertThat(id).matches("[0-9a-f-]{36}"))
                .doesNotHaveDuplicates();
    }

    @Test
    void laLlaveSeMantieneYElCorrelationIdCambiaEnCadaPublicacion() {
        Pago pago = pago("EQA-000001", EstadoEnvio.PENDIENTE, EstadoEnvio.ENVIADO);

        MensajePago primero = MensajePago.pendientesDe(pago).get(0);
        MensajePago segundo = MensajePago.pendientesDe(pago).get(0);

        // llave estable: la misma carta siempre lleva la misma direccion
        assertThat(segundo.numeroDocumento()).isEqualTo(primero.numeroDocumento());
        // seguimiento: cada viaje tiene su propio numero
        assertThat(segundo.correlationId()).isNotEqualTo(primero.correlationId());
    }

    private Pago pago(String numero, EstadoEnvio banco, EstadoEnvio erp) {
        Pago pago = new Pago();
        ReflectionTestUtils.setField(pago, "numeroDocumento", numero);
        ReflectionTestUtils.setField(pago, "codigoCliente", "CLI-00001");
        ReflectionTestUtils.setField(pago, "nombreCliente", "Cliente de Prueba");
        ReflectionTestUtils.setField(pago, "codigoContador", "MTR-0001");
        ReflectionTestUtils.setField(pago, "periodoAnio", 2026);
        ReflectionTestUtils.setField(pago, "periodoMes", 10);
        ReflectionTestUtils.setField(pago, "montoCentavos", 15075L);
        ReflectionTestUtils.setField(pago, "consumoCentavos", 15075L);
        ReflectionTestUtils.setField(pago, "adicionalCentavos", 0L);
        ReflectionTestUtils.setField(pago, "fechaPago", LocalDateTime.of(2026, 10, 1, 9, 30));
        ReflectionTestUtils.setField(pago, "formaPago", "efectivo");
        ReflectionTestUtils.setField(pago, "estadoEnvioBanco", banco);
        ReflectionTestUtils.setField(pago, "estadoEnvioErp", erp);
        return pago;
    }
}