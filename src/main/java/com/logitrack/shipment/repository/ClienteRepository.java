package com.logitrack.shipment.repository;

import com.logitrack.shipment.model.Cliente;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/**
 * Mantiene identidades logísticas sin duplicados ante primeras altas concurrentes.
 * @author dominox45
 */
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    /**
     * Inserta la identidad si no existe, sin actualizar su fecha original.
     * @param id identidad previamente autenticada o validada por Security
     * @return filas insertadas (0 si ya existía)
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "INSERT IGNORE INTO clientes(id, created_date) VALUES (:id, CURRENT_TIMESTAMP(6))", nativeQuery = true)
    int asegurarExistencia(@Param("id") UUID id);
}
