package oficinaagua.consumer.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import oficinaagua.consumer.pago.Destino;
import oficinaagua.consumer.pago.MensajePago;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** HU-45: reglas del contrato del ERP. Sin Spring, sin red. */
class ErpMapperTest {

    private final ErpMapper mapper = new ErpMapper("EQUIPO_A");

    @ParameterizedTest
    @CsvSource({"15075,150.75", "1000,10.00", "5,0.05", "0,0.00", "100000,1000.00"})
    void losCentavosSeConviertenATextoConDosDecimales(long centavos, String esperado) {
        assertThat(ErpMapper.monto(centavos)).isEqualTo(esperado);
    }

    @Test
    void pagoConMora_comoElEjemploDelContrato() {
        ErpRegistroPago registro = mapper.aRegistro(mensaje(15075, 14075, 1000, "Mora", "efectivo"));

        assertThat(registro.equipo()).isEqualTo("EQUIPO_A");
        assertThat(registro.documentoOrigen().tipo()).isEqualTo("RECIBO_CAJA");
        assertThat(registro.documentoOrigen().numero()).isEqualTo("EQA-000123");
        assertThat(registro.documentoOrigen().fechaEmision()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(registro.periodoFiscal()).isEqualTo(new ErpPeriodo(2026, 9));
        assertThat(registro.formaPago()).isEqualTo("EFECTIVO");
        assertThat(registro.fechaPago().toString()).isEqualTo("2026-10-01T10:15-06:00");
        assertThat(registro.total()).isEqualTo("150.75");
        assertThat(registro.detalle())
                .extracting(ErpLineaDetalle::rubro, ErpLineaDetalle::monto)
                .containsExactly(tuple("AGUA_POTABLE", "140.75"), tuple("MORA", "10.00"));
    }

    @Test
    void sinAdicional_unaSolaLineaDeAguaPotable() {
        ErpRegistroPago registro = mapper.aRegistro(mensaje(15075, 15075, 0, null, "efectivo"));

        assertThat(registro.detalle())
                .extracting(ErpLineaDetalle::rubro, ErpLineaDetalle::monto)
                .containsExactly(tuple("AGUA_POTABLE", "150.75"));
    }

    @ParameterizedTest
    @CsvSource({"Mora por atraso,MORA", "Reconexión del servicio,RECONEXION",
            "Nueva conexion,NUEVA_CONEXION", "Instalación de contador,NUEVA_CONEXION", "Multa,OTROS"})
    void elConceptoAdicionalSeMapeaAUnRubro(String concepto, String rubroEsperado) {
        assertThat(ErpMapper.rubro(concepto)).isEqualTo(rubroEsperado);
    }

    @Test
    void rubroOtros_llevaElConceptoComoDescripcion() {
        ErpRegistroPago registro = mapper.aRegistro(mensaje(16075, 15075, 1000, "Multa", "efectivo"));

        ErpLineaDetalle otros = registro.detalle().get(1);
        assertThat(otros.rubro()).isEqualTo("OTROS");
        assertThat(otros.descripcion()).isEqualTo("Multa");
    }

    @Test
    void siElDetalleNoCuadra_fallaAntesDeLlamarAlErp() {
        // 140.75 + 5.00 != 150.75
        assertThatThrownBy(() -> mapper.aRegistro(mensaje(15075, 14075, 500, "Mora", "efectivo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no cuadra");
    }

    @ParameterizedTest
    @CsvSource({"EFECTIVO,EFECTIVO", "tarjeta,TARJETA", "transferencia,TRANSFERENCIA", "cheque,CHEQUE"})
    void laFormaDePagoSeMapeaAlCatalogoDelErp(String formaPago, String esperado) {
        assertThat(mapper.aRegistro(mensaje(15075, 15075, 0, null, formaPago)).formaPago())
                .isEqualTo(esperado);
    }

    @Test
    void formaDePagoDesconocida_falla() {
        assertThatThrownBy(() -> mapper.aRegistro(mensaje(15075, 15075, 0, null, "bitcoin")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numeroDeDocumentoInvalido_falla() {
        MensajePago conEspacio = new MensajePago("EQA 000123", Destino.ERP, "CLI-0001", "Ana Lopez",
                "MTR-00001", 2026, 9, 15075, 15075, 0, null, LocalDateTime.of(2026, 10, 1, 10, 15),
                "efectivo", null);

        assertThatThrownBy(() -> mapper.aRegistro(conEspacio))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MensajePago mensaje(long total, long consumo, long adicional, String concepto, String formaPago) {
        return new MensajePago("EQA-000123", Destino.ERP, "CLI-0001", "Ana Lopez", "MTR-00001",
                2026, 9, total, consumo, adicional, concepto, LocalDateTime.of(2026, 10, 1, 10, 15),
                formaPago, null);
    }
}