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
import pe.gob.munisanmiguel.service.DiligenciaFiscalizacionService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DiligenciaFiscalizacionController.class,ActaFiscalizacionController.class})
@Import(SecurityConfig.class)
class DiligenciaAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean DiligenciaFiscalizacionService service;
    @MockitoBean JwtService jwt;
    @MockitoBean AppUserRepository users;

    @Test void endpointDeTrabajoDeCampoSinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/fiscalizador/programaciones/7/diligencia")).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(authorities={"EXPEDIENTE_VER","EXPEDIENTE_CREAR","USUARIOS_VER","ROLES_VER"})
    void municipioYAdminNoPuedenIniciarNiFinalizarDiligencias() throws Exception {
        mvc.perform(post("/api/fiscalizador/programaciones/7/diligencia/iniciar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoGps\":\"NO_AUTORIZADO\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/fiscalizador/diligencias/9/finalizar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firmaFiscalizado\":\"FIRMA_PENDIENTE\",\"copiaEntregada\":false,\"medioEntrega\":\"PENDIENTE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities="DILIGENCIA_INICIAR")
    void capacidadDeInicioHabilitaElEndpointDeInicio() throws Exception {
        mvc.perform(post("/api/fiscalizador/programaciones/7/diligencia/iniciar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoGps\":\"NO_AUTORIZADO\"}")).andExpect(status().isOk());
        verify(service).iniciar(eq(7L),any(),eq("user"));
    }

    @Test @WithMockUser(authorities="DILIGENCIA_INICIAR")
    void capacidadDeInicioHabilitaRegistroPosteriorPropio() throws Exception {
        mvc.perform(post("/api/fiscalizador/programaciones/7/diligencia/registrar-posterior")
                .contentType(MediaType.APPLICATION_JSON).content("{\"fechaEjecucion\":\"2026-10-08\",\"horaEjecucion\":\"23:59\"}"))
                .andExpect(status().isOk());
        verify(service).registrarPosterior(eq(7L),any(),eq("user"));
    }

    @Test @WithMockUser(authorities="TAREAS_PROPIAS_VER")
    void soloConsultaNoPermiteRegistroPosterior() throws Exception {
        mvc.perform(post("/api/fiscalizador/programaciones/7/diligencia/registrar-posterior")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities="DILIGENCIA_EDITAR_PROPIA")
    void personaAusenteUsaElEnumAceptadoPorBackend() throws Exception {
        mvc.perform(patch("/api/fiscalizador/diligencias/9").contentType(MediaType.APPLICATION_JSON)
                .content("{\"situacionPersona\":\"ADMINISTRADO_AUSENTE\"}"))
                .andExpect(status().isOk());
        verify(service).guardar(eq(9L),argThat(r->r.situacionPersona()==pe.gob.munisanmiguel.entity.SituacionPersona.ADMINISTRADO_AUSENTE),eq("user"));
    }

    @Test @WithMockUser(authorities="ACTA_VER")
    void municipioConActaVerPuedeConsultarActas() throws Exception {
        when(service.actasExpediente("FIS-TEST","user",false)).thenReturn(java.util.List.of());
        mvc.perform(get("/api/actas/expediente/FIS-TEST")).andExpect(status().isOk());
        verify(service).actasExpediente("FIS-TEST","user",false);
    }

    @Test @WithMockUser(authorities="ACTA_VER")
    void permisoDeConsultaNoPermiteSubirCopiaFirmada() throws Exception {
        mvc.perform(multipart("/api/actas/1/copia-firmada").file("archivo","%PDF-1.4\n%%EOF".getBytes()))
                .andExpect(status().isForbidden());
    }
}
