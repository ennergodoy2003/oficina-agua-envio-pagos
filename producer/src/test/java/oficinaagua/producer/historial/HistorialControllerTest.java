package oficinaagua.producer.historial;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import oficinaagua.producer.envio.ManejadorErrores;
import oficinaagua.producer.pago.Destino;
import oficinaagua.producer.pago.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HU-46: GET /api/v1/pagos/{pagoId}/envios responde lo que dice el contrato.
 * MockMvc standalone: sin Spring, sin MySQL, sin DynamoDB.
 */
class HistorialControllerTest {

    private PagoRepository pagos;
    private HistorialEnvioRepository historial;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        pagos = mock(PagoRepository.class);
        historial = mock(HistorialEnvioRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new HistorialController(pagos, historial))
                .setControllerAdvice(new ManejadorErrores())
                .build();
    }

    @Test
    void pagoConFallo_responde200_conMotivoFallo() throws Exception {
        when(pagos.existsByNumeroDocumento("EQA-000003")).thenReturn(true);
        when(historial.intentosDe("EQA-000003")).thenReturn(List.of(new IntentoEnvio(
                "EQA-000003", Destino.BANCO, IntentoEnvio.FALLO, "2026-10-20T18:12:31.649Z",
                "El banco respondio 503 SERVICE_UNAVAILABLE")));

        mvc.perform(get("/api/v1/pagos/EQA-000003/envios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pagoId").value("EQA-000003"))
                .andExpect(jsonPath("$[0].destino").value("BANCO"))
                .andExpect(jsonPath("$[0].resultado").value("FALLO"))
                .andExpect(jsonPath("$[0].fecha").value("2026-10-20T18:12:31.649Z"))
                .andExpect(jsonPath("$[0].motivoFallo").value("El banco respondio 503 SERVICE_UNAVAILABLE"));
    }

    @Test
    void pagoConExito_responde200_sinMotivoFallo() throws Exception {
        when(pagos.existsByNumeroDocumento("EQA-000001")).thenReturn(true);
        when(historial.intentosDe("EQA-000001")).thenReturn(List.of(new IntentoEnvio(
                "EQA-000001", Destino.BANCO, IntentoEnvio.EXITO, "2026-10-20T18:12:30.883Z", null)));

        mvc.perform(get("/api/v1/pagos/EQA-000001/envios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resultado").value("EXITO"))
                .andExpect(jsonPath("$[0].motivoFallo").doesNotExist());
    }

    @Test
    void pagoSinIntentos_responde200_conListaVacia() throws Exception {
        when(pagos.existsByNumeroDocumento("EQA-000099")).thenReturn(true);
        when(historial.intentosDe("EQA-000099")).thenReturn(List.of());

        mvc.perform(get("/api/v1/pagos/EQA-000099/envios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void pagoInexistente_responde404_sinConsultarDynamo() throws Exception {
        when(pagos.existsByNumeroDocumento("NOEXISTE-999")).thenReturn(false);

        mvc.perform(get("/api/v1/pagos/NOEXISTE-999/envios"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PAGO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No existe el pago NOEXISTE-999"));

        // Si el pago no existe, ni siquiera se pregunta a DynamoDB
        verify(historial, never()).intentosDe(any());
    }
}