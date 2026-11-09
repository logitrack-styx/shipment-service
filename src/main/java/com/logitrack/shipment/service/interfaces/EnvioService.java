
package com.logitrack.shipment.service.interfaces;

import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import com.logitrack.shipment.dto.request.EnvioRequest;
import com.logitrack.shipment.dto.response.EnvioResponse;
import com.logitrack.shipment.enums.EstadoEnvio;

/**
 * Casos de uso del ciclo de vida de los envíos. La asignación de repartidores
 * pertenece a {@code AsignacionService} y no se incluye aquí.
 *
 * @author dominox45
 */
public interface EnvioService {
    /**
     * @param estado filtro opcional
     * @param jwt identidad ADMIN
     * @param page página desde cero
     * @param size tamaño de 1 a 100
     * @return página limitada de envíos
     */
    org.springframework.data.domain.Page<EnvioResponse> listar(EstadoEnvio estado, Jwt jwt, int page, int size);

    /**
     * Lists only shipments owned by the authenticated customer.
     * @author dominox45
     */
    org.springframework.data.domain.Page<EnvioResponse> listarPropios(EstadoEnvio estado, Jwt jwt, int page, int size);

    /**
     * Lists only shipments assigned to the authenticated courier.
     * @author dominox45
     */
    org.springframework.data.domain.Page<EnvioResponse> listarAsignados(EstadoEnvio estado, Jwt jwt, int page, int size);


    /**
     * Consulta un envío por su identificador.
     *
     * @param id identificador del envío
     * @return el envío encontrado
     */
    EnvioResponse obtenerPorId(UUID id);

    /**
     * Consulta un envío por su código de seguimiento.
     *
     * @param codigoSeguimiento código de seguimiento
     * @return el envío encontrado
     */
    EnvioResponse obtenerPorCodigoSeguimiento(UUID codigoSeguimiento);

    /**
     * Lista envíos, opcionalmente filtrados por estado.
     *
     * @param estado estado a filtrar; {@code null} devuelve todos
     * @return lista de envíos
     */
    List<EnvioResponse> listar(EstadoEnvio estado);

    /**
     * Lista envíos usando el JWT del usuario autenticado.
     *
     * @param estado estado a filtrar; {@code null} devuelve todos
     * @param jwt token del usuario autenticado
     * @return lista de envíos
     */
    List<EnvioResponse> listar(EstadoEnvio estado, Jwt jwt);

    /**
     * Cambia el estado de un envío respetando la máquina de estados del dominio.
     *
     * @param id identificador del envío
     * @param nuevoEstado estado de destino
     * @return el envío actualizado
     */
    EnvioResponse cambiarEstado(UUID id, EstadoEnvio nuevoEstado);

    /**
     * Cancela un envío; solo es válido si está PENDIENTE.
     *
     * @param id identificador del envío
     */
    void cancelarEnvio(UUID id);

    EnvioResponse registrar(
            EnvioRequest request,
            UUID authenticatedUserId,
            String role);

    EnvioResponse obtenerPorId(
            UUID id,
            UUID authenticatedUserId,
            String role);

    EnvioResponse obtenerPorCodigoSeguimiento(
            UUID codigoSeguimiento,
            UUID authenticatedUserId,
            String role);

    EnvioResponse cambiarEstado(
            UUID id,
            EstadoEnvio nuevoEstado,
            UUID authenticatedUserId,
            String role);

    void cancelarEnvio(
            UUID id,
            UUID authenticatedUserId,
            String role);
}
