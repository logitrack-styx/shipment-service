
package com.logitrack.shipment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.logitrack.shipment.enums.EstadoEnvio;
import com.logitrack.shipment.model.Envio;

@Repository
public interface EnvioRepository extends JpaRepository<Envio, UUID> {

    Optional<Envio> findByCodigoSeguimiento(UUID codigoSeguimiento);

    /**
     * Serializes assignment requests for an existing shipment inside a transaction.
     * @author dominox45
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Envio e where e.id = :id")
    Optional<Envio> findForAssignment(@Param("id") UUID id);

    default List<Envio> findByEstado(EstadoEnvio estado) {
        return findByEstado(estado, com.logitrack.shipment.config.BoundedPage.of(0, 20)).getContent();
    }
    org.springframework.data.domain.Page<Envio> findByEstado(EstadoEnvio estado, org.springframework.data.domain.Pageable pageable);

    /**
     * Asigna un repartidor únicamente si el envío conserva el estado esperado
     * y todavía no tiene un repartidor asignado.
     *
     * <p>La consulta masiva no ejecuta callbacks de auditoría JPA; por eso
     * actualiza explícitamente la fecha y la versión de la entidad.</p>
     *
     * @param envioId identificador del envío
     * @param repartidorId identificador del repartidor
     * @param estado estado requerido por la asignación
     * @param fechaModificacion instante de la escritura
     * @return 1 si se actualizó una fila; 0 si no se cumplieron las condiciones
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update Envio e
           set e.repartidorId = :repartidorId,
               e.version = e.version + 1,
               e.lastModifiedDate = :fechaModificacion
         where e.id = :envioId
           and e.estado = :estado
           and e.repartidorId is null
        """)
    int asignarRepartidorSiDisponible(
            @Param("envioId") UUID envioId,
            @Param("repartidorId") UUID repartidorId,
            @Param("estado") EstadoEnvio estado,
            @Param("fechaModificacion") Instant fechaModificacion);

    /**
     * Ejecuta la asignación atómica utilizando el instante actual.
     *
     * @param envioId identificador del envío
     * @param repartidorId identificador del repartidor
     * @param estado estado requerido por la asignación
     * @return cantidad de filas actualizadas
     */
    default int asignarRepartidorSiDisponible(
            UUID envioId, UUID repartidorId, EstadoEnvio estado) {
        return asignarRepartidorSiDisponible(
                envioId, repartidorId, estado, Instant.now());
    }

    /**
     * Compatibilidad con el nombre anterior del método.
     */
    default int asignarRepartidorSiPendienteSinAsignacion(
            UUID envioId,
            UUID repartidorId) {

        return asignarRepartidorSiDisponible(
                envioId,
                repartidorId,
                EstadoEnvio.PENDIENTE);
    }

    org.springframework.data.domain.Page<Envio> findByClienteId(UUID clienteId, org.springframework.data.domain.Pageable pageable);
    default List<Envio> findByClienteId(UUID clienteId) {
        return findByClienteId(clienteId, com.logitrack.shipment.config.BoundedPage.of(0, 20)).getContent();
    }

    org.springframework.data.domain.Page<Envio> findByClienteIdAndEstado(UUID clienteId, EstadoEnvio estado, org.springframework.data.domain.Pageable pageable);
    default List<Envio> findByClienteIdAndEstado(UUID clienteId, EstadoEnvio estado) {
        return findByClienteIdAndEstado(clienteId, estado, com.logitrack.shipment.config.BoundedPage.of(0, 20)).getContent();
    }

    org.springframework.data.domain.Page<Envio> findByRepartidorId(UUID repartidorId, org.springframework.data.domain.Pageable pageable);
    default List<Envio> findByRepartidorId(UUID repartidorId) {
        return findByRepartidorId(repartidorId, com.logitrack.shipment.config.BoundedPage.of(0, 20)).getContent();
    }

    org.springframework.data.domain.Page<Envio> findByRepartidorIdAndEstado(UUID repartidorId, EstadoEnvio estado, org.springframework.data.domain.Pageable pageable);
    default List<Envio> findByRepartidorIdAndEstado(UUID repartidorId, EstadoEnvio estado) {
        return findByRepartidorIdAndEstado(repartidorId, estado, com.logitrack.shipment.config.BoundedPage.of(0, 20)).getContent();
    }
}
