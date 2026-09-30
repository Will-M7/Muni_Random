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
import pe.gob.munisanmiguel.service.RoleCapabilityService;
import pe.gob.munisanmiguel.service.UsuarioService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest({UsuarioController.class, AdminController.class, CatalogoController.class})
@Import(SecurityConfig.class)
class RoleSeparationAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioService usuarios;
    @MockitoBean RoleCapabilityService roleCapabilities;
    @MockitoBean JwtService jwtService;
    @MockitoBean AppUserRepository userRepository;

    @Test @WithMockUser(authorities = {"USUARIOS_VER", "USUARIOS_CREAR"})
    void adminPuedeCrearUsuarios() throws Exception {
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nuevo\",\"nombre\":\"Usuario Nuevo\",\"password\":\"clave-segura-123\",\"confirmarPassword\":\"clave-segura-123\",\"rol\":\"MUNICIPIO\"}"))
                .andExpect(status().isCreated());
        verify(usuarios).crear(any());
    }

    @Test @WithMockUser(authorities = {"SOLICITUD_VER", "FISCALIZADORES_DISPONIBLES_VER"})
    void municipioNoPuedeAdministrarUsuariosNiRoles() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nuevo\",\"nombre\":\"Usuario Nuevo\",\"password\":\"clave-segura-123\",\"confirmarPassword\":\"clave-segura-123\",\"rol\":\"FISCALIZADOR\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/roles")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/capacidades")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = {"ROLES_VER", "ROLES_GESTIONAR", "CAPACIDADES_GESTIONAR", "USUARIOS_VER", "USUARIOS_CREAR"})
    void adminPuedeConsultarRolesYCapacidades() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/roles")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/capacidades")).andExpect(status().isOk());
    }

    @Test @WithMockUser(authorities = "FISCALIZADORES_DISPONIBLES_VER")
    void directorioPermiteSoloConsultarFiscalizadoresDisponibles() throws Exception {
        when(usuarios.fiscalizadoresDisponibles()).thenReturn(java.util.List.of(
                new pe.gob.munisanmiguel.dto.ApiDtos.FiscalizadorDisponibleResponse("FIS-001", "Inspector", "Zona Centro", true)));
        mvc.perform(get("/api/catalogos/fiscalizadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("FIS-001"))
                .andExpect(jsonPath("$[0].nombre").value("Inspector"))
                .andExpect(jsonPath("$[0].activo").value(true))
                .andExpect(jsonPath("$[0].telefono").doesNotExist())
                .andExpect(jsonPath("$[0].codigo").doesNotExist());
        verify(usuarios).fiscalizadoresDisponibles();
        mvc.perform(get("/api/usuarios/fiscalizadores")).andExpect(status().isNotFound());
    }

    @Test @WithMockUser(authorities = {"TAREAS_PROPIAS_VER", "RESULTADO_REGISTRAR"})
    void fiscalizadorNoPuedeConsultarDirectorioMunicipal() throws Exception {
        mvc.perform(get("/api/catalogos/fiscalizadores")).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
    }
}
