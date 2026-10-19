package oficinaagua.producer.pago;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** Lee la vista v_pagos_envio: el producer nunca escribe en la base. */
@Entity
@Immutable
@Table(name = "v_pagos_envio")
public class Pago {

    @Id
    private Long id;

    @Column(name = "numero_documento")
    private String numeroDocumento;

    @Column(name = "codigo_cliente")
    private String codigoCliente;

    @Column(name = "nombre_cliente")
    private String nombreCliente;

    @Column(name = "codigo_contador")
    private String codigoContador;

    @Column(name = "periodo_anio")
    private int periodoAnio;

    @Column(name = "periodo_mes")
    private int periodoMes;

    @Column(name = "monto_centavos")
    private long montoCentavos;

    @Column(name = "consumo_centavos")
    private long consumoCentavos;

    @Column(name = "adicional_centavos")
    private long adicionalCentavos;

    @Column(name = "concepto_adicional")
    private String conceptoAdicional;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "forma_pago")
    private String formaPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_envio_banco")
    private EstadoEnvio estadoEnvioBanco;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_envio_erp")
    private EstadoEnvio estadoEnvioErp;

    protected Pago() {
        // Constructor que exige JPA. Nadie crea pagos desde el producer.
    }

    public Long getId() { return id; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public String getCodigoCliente() { return codigoCliente; }
    public String getNombreCliente() { return nombreCliente; }
    public String getCodigoContador() { return codigoContador; }
    public int getPeriodoAnio() { return periodoAnio; }
    public int getPeriodoMes() { return periodoMes; }
    public long getMontoCentavos() { return montoCentavos; }
    public long getConsumoCentavos() { return consumoCentavos; }
    public long getAdicionalCentavos() { return adicionalCentavos; }
    public String getConceptoAdicional() { return conceptoAdicional; }
    public LocalDateTime getFechaPago() { return fechaPago; }
    public String getFormaPago() { return formaPago; }
    public EstadoEnvio getEstadoEnvioBanco() { return estadoEnvioBanco; }
    public EstadoEnvio getEstadoEnvioErp() { return estadoEnvioErp; }
}

