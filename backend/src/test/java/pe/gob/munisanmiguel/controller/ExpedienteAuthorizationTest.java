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
import pe.gob.munisanmiguel.service.ExpedienteFiscalizacionService;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ExpedienteFiscalizacionController.class,ProgramacionFiscalizacionController.class,FiscalizadorController.class})
@Import(SecurityConfig.class)
class ExpedienteAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean ExpedienteFiscalizacionService service;
    @MockitoBean pe.gob.munisanmiguel.service.SolicitudService solicitudService;
    @MockitoBean JwtService jwt;
    @MockitoBean AppUserRepository users;

    @Test @WithMockUser(authorities="EXPEDIENTE_VER")
    void municipioConsultaExpedientes(){
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(()->mvc.perform(get("/api/expedientes")).andExpect(status().isOk()));
        verify(service).vencerProgramaciones();
    }
    @Test @WithMockUser(authorities={"USUARIOS_VER","ROLES_VER","ROLES_GESTIONAR"})
    void adminTecnicoNoPuedeOperarExpedientes() throws Exception {
        mvc.perform(get("/api/expedientes")).andExpect(status().isForbidden());
        mvc.perform(post("/api/expedientes/SM-2026-1/programaciones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"2026-10-01\",\"hora\":\"10:00\",\"tipoVisita\":\"PROGRAMADA\"}"))
                .andExpect(status().isForbidden());
    }
    @Test @WithMockUser(authorities={"TAREAS_PROPIAS_VER","FISCALIZACION_EJECUTAR"})
    void fiscalizadorNoPuedeReprogramarNiCancelar() throws Exception {
        mvc.perform(post("/api/programaciones/5/reprogramar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"2026-10-01\",\"hora\":\"10:00\",\"motivo\":\"Cambio de agenda\",\"tipoVisita\":\"PROGRAMADA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/programaciones/5/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Cancelación\"}"))
                .andExpect(status().isForbidden());
    }
    @Test @WithMockUser(authorities="TAREAS_PROPIAS_VER")
    void fiscalizadorPuedeConsultarAsignacionesDeExpedientes() throws Exception {
        mvc.perform(get("/api/fiscalizador/mis-expedientes")).andExpect(status().isOk());
        verify(service).misTareasCampo("user");
    }
    @Test @WithMockUser(authorities="EXPEDIENTE_VER")
    void municipioSinPermisoDeTareasNoConsultaAsignacionesDeFiscalizador() throws Exception {
        mvc.perform(get("/api/fiscalizador/mis-expedientes")).andExpect(status().isForbidden());
    }
    @Test
    void asignacionesSinAutenticacionRequierenLogin() throws Exception {
        mvc.perform(get("/api/fiscalizador/mis-expedientes")).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser(authorities={"EXPEDIENTE_CREAR","PROGRAMACION_CREAR","FISCALIZADOR_ASIGNAR","PROGRAMACION_REPROGRAMAR","PROGRAMACION_CANCELAR"})
    void municipioPuedeInvocarCreacionYReprogramacion() throws Exception {
        mvc.perform(post("/api/expedientes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"origen\":\"INICIATIVA_MUNICIPAL\",\"objetoFiscalizacion\":\"Inspección de local\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/expedientes/FIS-2026-00001/programaciones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"2026-10-01\",\"hora\":\"10:00\",\"tipoVisita\":\"INOPINADA\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/programaciones/5/reprogramar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"2026-10-02\",\"hora\":\"11:00\",\"motivo\":\"Cambio\",\"tipoVisita\":\"PROGRAMADA\"}"))
                .andExpect(status().isOk());
        verify(service).crear(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("user"));
        verify(service).reprogramar(org.mockito.ArgumentMatchers.eq(5L),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("user"));
    }
}
