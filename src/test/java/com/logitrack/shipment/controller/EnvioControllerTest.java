package com.logitrack.shipment.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.eq;

import com.logitrack.shipment.enums.EstadoEnvio;
import com.logitrack.shipment.service.interfaces.EnvioService;

/**
 * Pruebas web del controlador de envíos.
 *
 * @author dominox45
 */
@WebMvcTest(controllers = EnvioController.class)
@AutoConfigureMockMvc(addFilters = false)
class EnvioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnvioService envioService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void debeListarEnviosSinFiltro() throws Exception {
        when(envioService.listar(
                isNull(EstadoEnvio.class),
                isNull(Jwt.class), eq(0), eq(20)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/envios"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(envioService).listar(
                isNull(EstadoEnvio.class),
                isNull(Jwt.class), eq(0), eq(20));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void debeListarEnviosFiltrandoPorEstado() throws Exception {
        when(envioService.listar(
                eq(EstadoEnvio.PENDIENTE),
                isNull(Jwt.class), eq(0), eq(20)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/envios")
                        .param("estado", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(envioService).listar(
                eq(EstadoEnvio.PENDIENTE),
                isNull(Jwt.class), eq(0), eq(20));
    }
}
