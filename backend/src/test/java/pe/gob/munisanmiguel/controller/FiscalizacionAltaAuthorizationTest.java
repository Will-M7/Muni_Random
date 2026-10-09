package pe.gob.munisanmiguel.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.gob.munisanmiguel.config.SecurityConfig;
import pe.gob.munisanmiguel.repository.AppUserRepository;
import pe.gob.munisanmiguel.security.JwtService;
import pe.gob.munisanmiguel.service.AltaFiscalizacionService;
import java.nio.charset.StandardCharsets;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(FiscalizacionAltaController.class)
@Import(SecurityConfig.class)
class FiscalizacionAltaAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean AltaFiscalizacionService service;
    @MockitoBean JwtService jwt;
    @MockitoBean pe.gob.munisanmiguel.service.AdminSettingsService settings;
    @MockitoBean AppUserRepository users;

    private MockMultipartFile payload(boolean programar){
        String json="{\"expediente\":{\"origen\":\"SOLICITUD\",\"objetoFiscalizacion\":\"Verificar predio\"},"
                +"\"dependenciaProcedencia\":\"Mesa de partes\",\"referenciaSolicitud\":\"MEM-21\""
                +(programar?",\"primeraProgramacion\":{\"fiscalizadorId\":\"F-1\",\"fecha\":\"2026-10-10\",\"hora\":\"08:00\",\"tipoVisita\":\"PROGRAMADA\"}":"")+"}";
        return new MockMultipartFile("fiscalizacion","","application/json",json.getBytes(StandardCharsets.UTF_8));
    }

    @Test @WithMockUser(authorities="EXPEDIENTE_CREAR")
    void altaCompuestaSinProgramacionLlegaAlServicio()throws Exception{
        mvc.perform(multipart("/api/expedientes/fiscalizaciones").file(payload(false)))
                .andExpect(status().isCreated());
        verify(service).crear(argThat(r->"MEM-21".equals(r.referenciaSolicitud())&&r.primeraProgramacion()==null),isNull(),eq("user"));
    }

    @Test @WithMockUser(authorities="EXPEDIENTE_CREAR")
    void programacionRequiereCapacidadesDeAsignacion()throws Exception{
        mvc.perform(multipart("/api/expedientes/fiscalizaciones").file(payload(true)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(authorities={"EXPEDIENTE_CREAR","EXPEDIENTE_EDITAR","PROGRAMACION_CREAR","FISCALIZADOR_ASIGNAR"})
    void altaConProgramacionYDocumentoEstaConectada()throws Exception{
        mvc.perform(multipart("/api/expedientes/fiscalizaciones").file(payload(true))
                .file(new MockMultipartFile("documentos","orden.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated());
        verify(service).crear(argThat(r->r.primeraProgramacion()!=null&&"F-1".equals(r.primeraProgramacion().fiscalizadorId())),
                argThat(files->files.size()==1),eq("user"));
    }

    @Test @WithMockUser(authorities="EXPEDIENTE_CREAR")
    void documentoInicialRequierePermisoDeEdicion()throws Exception{
        mvc.perform(multipart("/api/expedientes/fiscalizaciones").file(payload(false))
                .file(new MockMultipartFile("documentos","orden.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(authorities="EXPEDIENTE_EDITAR")
    void municipioPuedeAdjuntarDocumentoPosterior()throws Exception{
        mvc.perform(multipart("/api/expedientes/FIS-TEST/documentos")
                .file(new MockMultipartFile("documentos","orden.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk());
        verify(service).adjuntar(eq("FIS-TEST"),argThat(files->files.size()==1),eq("user"));
    }
    @Test @WithMockUser(authorities="EXPEDIENTE_VER")
    void consultaSinEdicionNoPuedeAdjuntar()throws Exception{
        mvc.perform(multipart("/api/expedientes/FIS-TEST/documentos")
                .file(new MockMultipartFile("documentos","orden.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(authorities="EXPEDIENTE_VER")
    void pdfSeEntregaInlineConAutenticacion()throws Exception{
        when(service.descargar("FIS-TEST",5L)).thenReturn(new AltaFiscalizacionService.DocumentoDescarga(
                new org.springframework.core.io.ByteArrayResource("%PDF-1.4".getBytes(StandardCharsets.UTF_8)),"acta.pdf","application/pdf"));
        mvc.perform(get("/api/expedientes/FIS-TEST/documentos/5/archivo"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",containsString("inline")));
    }
}
