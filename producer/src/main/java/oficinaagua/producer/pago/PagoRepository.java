package oficinaagua.producer.pago;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PagoRepository extends Repository<Pago, Long> {

    @Query("""
            select p from Pago p
            where p.estadoEnvioBanco = :estado or p.estadoEnvioErp = :estado
            order by p.id
            """)
    List<Pago> buscarPendientes(@Param("estado") EstadoEnvio estado, Pageable pageable);
    /** HU-46: para el 404 del historial. La vista tiene todos los pagos, no solo los pendientes. */
    boolean existsByNumeroDocumento(String numeroDocumento);
}

