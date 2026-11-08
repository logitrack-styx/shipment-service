package com.logitrack.shipment.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/**
 * Identidad logística local propietaria de envíos. Su UUID referencia lógicamente
 * al usuario de Security; no almacena credenciales ni autoridad de autenticación.
 * El perfil se crea al primer envío y conserva la identidad de registros históricos.
 * @author dominox45
 */
@Entity
@Table(name = "clientes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cliente {
    @Id
    private UUID id;
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;
}
