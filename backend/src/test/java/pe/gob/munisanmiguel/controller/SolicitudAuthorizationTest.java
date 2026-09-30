package pe.gob.munisanmiguel.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.gob.munisanmiguel.config.SecurityConfig;
import pe.gob.munisanmiguel.repository.AppUserRepository;
import pe.gob.munisanmiguel.security.JwtService;
import pe.gob.munisanmiguel.service.SolicitudService;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SolicitudController.class)
@Import(SecurityConfig.class)
class SolicitudAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean SolicitudService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AppUserRepository users;

    @Test void estadoSinTokenRequiereAutenticacion() throws Exception {
        mvc.perform(patch("/api/solicitudes/SM-100/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"OBSERVADA\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(authorities = "ROLE_FISCALIZADOR")
    void fiscalizadorNoPuedeCambiarEstadoAdministrativo() throws Exception {
        mvc.perform(patch("/api/solicitudes/SM-100/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"OBSERVADA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = "SOLICITUD_ESTADO_CAMBIAR")
    void capacidadPermiteCambiarEstadoAdministrativo() throws Exception {
        mvc.perform(patch("/api/solicitudes/SM-100/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"OBSERVADA\"}"))
                .andExpect(status().isOk());
        verify(service).cambiarEstado(org.mockito.ArgumentMatchers.eq("SM-100"), org.mockito.ArgumentMatchers.any());
    }
}
