
package com.logitrack.shipment.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.logitrack.shipment.dto.request.EnvioRequest;
import com.logitrack.shipment.dto.response.EnvioResponse;
import com.logitrack.shipment.enums.EstadoEnvio;
import com.logitrack.shipment.event.EnvioEstadoCambiadoEvent;
import com.logitrack.shipment.exception.RecursoNoEncontradoException;
import com.logitrack.shipment.model.Envio;
import com.logitrack.shipment.repository.EnvioRepository;
import com.logitrack.shipment.service.interfaces.EnvioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EnvioServiceImpl implements EnvioService {

    /**
     * @param estado filtro opcional
     * @param jwt identidad autorizada
     * @param page página desde cero
     * @param size máximo de 100 filas
     * @return página de resultados
     */
    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<EnvioResponse> listar(EstadoEnvio estado, Jwt jwt, int page, int size) {
        if (!"ADMIN".equals(obtenerRol(jwt))) throw new AccessDeniedException("Solo ADMIN puede listar envíos");
        var pageable = com.logitrack.shipment.config.BoundedPage.of(page, size);
        return (estado == null ? envioRepository.findAll(pageable) : envioRepository.findByEstado(estado, pageable))
                .map(EnvioResponse::from);
    }

    private final EnvioRepository envioRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.logitrack.shipment.repository.ClienteRepository clienteRepository;
    private final com.logitrack.shipment.client.ClienteIdentityVerifier clienteIdentityVerifier;

    /**
     * Variante heredada: solo ADMIN puede consultar sin comprobar pertenencia.
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public EnvioResponse obtenerPorId(UUID id) {
        return EnvioResponse.from(buscarPorId(id));
    }

    /**
     * Variante heredada: solo ADMIN puede consultar sin comprobar pertenencia.
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public EnvioResponse obtenerPorCodigoSeguimiento(UUID codigoSeguimiento) {
        return EnvioResponse.from(buscarPorCodigoSeguimiento(codigoSeguimiento));
    }

    /**
     * Variante heredada: solo ADMIN puede listar sin pasar identidad.
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<EnvioResponse> listar(EstadoEnvio estado) {
        var page = com.logitrack.shipment.config.BoundedPage.of(0, 20);
        List<Envio> envios = (estado == null
                ? envioRepository.findAll(page)
                : envioRepository.findByEstado(estado, page)).getContent();

        return envios.stream()
                .map(EnvioResponse::from)
                .toList();
    }

    /**
     * El listado con JWT queda restringido a ADMIN.
     */
    @Override
    @Transactional(readOnly = true)
    public List<EnvioResponse> listar(EstadoEnvio estado, Jwt jwt) {
        String role = obtenerRol(jwt);

        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException(
                    "Solo un administrador puede listar los envíos");
        }

        return listar(estado);
    }

    /**
     * Variante heredada: solo ADMIN puede cambiar estados sin identidad.
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public EnvioResponse cambiarEstado(UUID id, EstadoEnvio nuevoEstado) {
        Envio envio = buscarPorId(id);
        EstadoEnvio estadoAnterior = envio.getEstado();

        envio.cambiarEstado(nuevoEstado);
        envio = envioRepository.save(envio);

        eventPublisher.publishEvent(
                new EnvioEstadoCambiadoEvent(
                        envio,
                        estadoAnterior,
                        envio.getEstado()));

        return EnvioResponse.from(envio);
    }

    /**
     * Variante heredada: solo ADMIN puede cancelar sin identidad.
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void cancelarEnvio(UUID id) {
        Envio envio = buscarPorId(id);
        EstadoEnvio estadoAnterior = envio.getEstado();

        envio.cancelar();
        envioRepository.save(envio);

        eventPublisher.publishEvent(
                new EnvioEstadoCambiadoEvent(
                        envio,
                        estadoAnterior,
                        envio.getEstado()));
    }

    /**
     * Registra un envío según el rol autenticado.
     *
     * CLIENTE siempre queda asociado al UUID autenticado.
     * ADMIN debe indicar el cliente destinatario en el request.
     */
    @Override
    public EnvioResponse registrar(
            EnvioRequest request,
            UUID authenticatedUserId,
            String role) {

        if ("CLIENTE".equals(role)) {
            return registrarParaCliente(request, authenticatedUserId);
        }

        if ("ADMIN".equals(role)) {
            if (request.clienteId() == null) {
                throw new IllegalArgumentException(
                        "ADMIN debe indicar el cliente destinatario");
            }

            clienteIdentityVerifier.verificar(request.clienteId());
            return registrarParaCliente(request, request.clienteId());
        }

        throw new AccessDeniedException(
                "El rol no tiene permiso para registrar envíos");
    }

    private EnvioResponse registrarParaCliente(
            EnvioRequest request,
            UUID clienteId) {

        clienteRepository.asegurarExistencia(clienteId);
        Envio envio = new Envio(
                clienteId,
                request.origen().aEntidad(),
                request.destino().aEntidad(),
                request.peso(),
                request.ancho(),
                request.alto(),
                request.largo());

        envio = envioRepository.save(envio);

        eventPublisher.publishEvent(
                new EnvioEstadoCambiadoEvent(
                        envio,
                        null,
                        envio.getEstado()));

        log.info(
                "Envío registrado con código de seguimiento {}",
                envio.getCodigoSeguimiento());

        return EnvioResponse.from(envio);
    }

    /**
     * Consulta un envío y valida la pertenencia del recurso.
     */
    @Override
    @Transactional(readOnly = true)
    public EnvioResponse obtenerPorId(
            UUID id,
            UUID authenticatedUserId,
            String role) {

        Envio envio = buscarPorId(id);
        verificarPertenencia(envio, authenticatedUserId, role);

        return EnvioResponse.from(envio);
    }

    /**
     * La búsqueda por tracking también valida la pertenencia.
     */
    @Override
    @Transactional(readOnly = true)
    public EnvioResponse obtenerPorCodigoSeguimiento(
            UUID codigoSeguimiento,
            UUID authenticatedUserId,
            String role) {

        Envio envio = buscarPorCodigoSeguimiento(codigoSeguimiento);
        verificarPertenencia(envio, authenticatedUserId, role);

        return EnvioResponse.from(envio);
    }

    /**
     * Solo ADMIN o el repartidor asignado pueden cambiar el estado.
     */
    @Override
    public EnvioResponse cambiarEstado(
            UUID id,
            EstadoEnvio nuevoEstado,
            UUID authenticatedUserId,
            String role) {

        if ("REPARTIDOR".equals(role)
                && nuevoEstado == EstadoEnvio.CANCELADO) {
            throw new AccessDeniedException(
                    "Un repartidor no puede cancelar un envío mediante el cambio de estado");
        }

        Envio envio = buscarPorId(id);

        if ("REPARTIDOR".equals(role)) {
            if (envio.getRepartidorId() == null
                    || !envio.getRepartidorId().equals(authenticatedUserId)) {
                throw new AccessDeniedException(
                        "El envío no está asignado al repartidor autenticado");
            }
        } else if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException(
                    "El rol no puede cambiar el estado del envío");
        }

        return cambiarEstado(id, nuevoEstado);
    }

    /**
     * CLIENTE solo puede cancelar sus propios envíos.
     * ADMIN puede cancelar cualquier envío que admita la operación.
     */
    @Override
    public void cancelarEnvio(
            UUID id,
            UUID authenticatedUserId,
            String role) {

        Envio envio = buscarPorId(id);

        if ("CLIENTE".equals(role)) {
            if (envio.getClienteId() == null
                    || !envio.getClienteId().equals(authenticatedUserId)) {
                throw new AccessDeniedException(
                        "El envío no pertenece al cliente autenticado");
            }
        } else if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException(
                    "El rol no puede cancelar envíos");
        }

        cancelarEnvio(id);
    }

    /*
     * Adaptadores para operaciones que reciben directamente un JWT.
     */

    public EnvioResponse registrar(EnvioRequest request, Jwt jwt) {
        return registrar(
                request,
                obtenerUsuarioId(jwt),
                obtenerRol(jwt));
    }

    public EnvioResponse obtenerPorId(UUID id, Jwt jwt) {
        return obtenerPorId(
                id,
                obtenerUsuarioId(jwt),
                obtenerRol(jwt));
    }

    public EnvioResponse obtenerPorCodigoSeguimiento(
            UUID codigoSeguimiento,
            Jwt jwt) {

        return obtenerPorCodigoSeguimiento(
                codigoSeguimiento,
                obtenerUsuarioId(jwt),
                obtenerRol(jwt));
    }

    public void cancelarEnvio(UUID id, Jwt jwt) {
        cancelarEnvio(
                id,
                obtenerUsuarioId(jwt),
                obtenerRol(jwt));
    }

    public EnvioResponse cambiarEstado(
            UUID id,
            EstadoEnvio nuevoEstado,
            Jwt jwt) {

        return cambiarEstado(
                id,
                nuevoEstado,
                obtenerUsuarioId(jwt),
                obtenerRol(jwt));
    }

    private UUID obtenerUsuarioId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException(
                    "El token no contiene un usuario autenticado");
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException(
                    "El subject del token no es un UUID válido");
        }
    }

    private String obtenerRol(Jwt jwt) {
        if (jwt == null) {
            throw new AccessDeniedException("Token no disponible");
        }

        Object rolesClaim = jwt.getClaims().get("roles");

        if (!(rolesClaim instanceof List<?> roles)
                || roles.size() != 1
                || !(roles.get(0) instanceof String role)
                || role.isBlank()) {

            throw new AccessDeniedException(
                    "El token no contiene un rol válido");
        }

        return role;
    }

    private void verificarPertenencia(
            Envio envio,
            UUID authenticatedUserId,
            String role) {

        if ("ADMIN".equals(role)) {
            return;
        }

        if ("CLIENTE".equals(role)
                && envio.getClienteId() != null
                && envio.getClienteId().equals(authenticatedUserId)) {
            return;
        }

        if ("REPARTIDOR".equals(role)
                && envio.getRepartidorId() != null
                && envio.getRepartidorId().equals(authenticatedUserId)) {
            return;
        }

        throw new AccessDeniedException(
                "No tiene acceso a este envío");
    }

    private Envio buscarPorId(UUID id) {
        return envioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Envío no encontrado: " + id));
    }

    private Envio buscarPorCodigoSeguimiento(UUID codigoSeguimiento) {
        return envioRepository.findByCodigoSeguimiento(codigoSeguimiento)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Envío no encontrado con código de seguimiento: "
                                + codigoSeguimiento));
    }
}
