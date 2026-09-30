package pe.gob.munisanmiguel.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.gob.munisanmiguel.config.SecurityConfig;
import pe.gob.munisanmiguel.repository.AppUserRepository;
import pe.gob.munisanmiguel.security.JwtService;
import pe.gob.munisanmiguel.service.RevisionFiscalizacionService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RevisionFiscalizacionController.class)
@Import(SecurityConfig.class)
class RevisionAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean RevisionFiscalizacionService service;
    @MockitoBean JwtService jwt;
    @MockitoBean AppUserRepository users;

    @Test @WithMockUser(authorities={"REVISION_VER","REVISION_INICIAR","ACLARACION_SOLICITAR","REVISION_CONCLUIR","EXPEDIENTE_CERRAR","SEGUIMIENTO_CREAR","INFORME_CONCLUSION_VER"})
    void municipioTieneCapacidadesDeRevision(){
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(()->mvc.perform(get("/api/revisiones/pendientes")).andExpect(status().isOk()));
    }
    @Test @WithMockUser(authorities={"ROLES_VER","USUARIOS_VER","CAPACIDADES_GESTIONAR"})
    void adminNoPuedeRevisarNiConcluir() throws Exception {
        mvc.perform(get("/api/revisiones/pendientes")).andExpect(status().isForbidden());
        mvc.perform(post("/api/revisiones/5/concluir").contentType("application/json").content("{\"conclusion\":\"CONFORMIDAD\",\"fundamentoConclusion\":\"Revisión de prueba\"}"))
                .andExpect(status().isForbidden());
    }
    @Test @WithMockUser(authorities={"TAREAS_PROPIAS_VER","DILIGENCIA_INICIAR"})
    void fiscalizadorNoPuedeVerNiConcluirRevision() throws Exception {
        mvc.perform(get("/api/revisiones/pendientes")).andExpect(status().isForbidden());
        mvc.perform(post("/api/revisiones/5/concluir").contentType("application/json").content("{\"conclusion\":\"CONFORMIDAD\",\"fundamentoConclusion\":\"Revisión de prueba\"}"))
                .andExpect(status().isForbidden());
    }
    @Test @WithMockUser(authorities={"ACLARACION_RESPONDER"})
    void fiscalizadorSoloPuedeAccederAclaracionesDeRespuesta() throws Exception {
        mvc.perform(post("/api/revisiones/5/concluir").contentType("application/json").content("{\"conclusion\":\"CONFORMIDAD\",\"fundamentoConclusion\":\"Revisión de prueba\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/revisiones/mis-aclaraciones")).andExpect(status().isOk());
    }
}
