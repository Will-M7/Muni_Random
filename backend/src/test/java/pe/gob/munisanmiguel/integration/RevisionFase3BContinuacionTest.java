package pe.gob.munisanmiguel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.*;
import pe.gob.munisanmiguel.service.FiscalizacionFileStorageService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Continues the persisted fixture without recreating any expediente or prior cycle. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.storage.diligencias-path=target/fase3b-integrada/diligencias",
        "app.storage.path=target/fase3b-integrada/solicitudes"
})
class RevisionFase3BContinuacionTest {
    private static final String MARKER = "VALIDACION_REVISION_FASE3B_INTEGRADA";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired AppRoleRepository roles;
    @Autowired FiscalizadorRepository fiscalizadores;
    @Autowired ExpedienteFiscalizacionRepository expedientes;
    @Autowired ProgramacionFiscalizacionRepository programaciones;
    @Autowired DiligenciaFiscalizacionRepository diligencias;
    @Autowired ActaFiscalizacionRepository actas;
    @Autowired RevisionFiscalizacionRepository revisiones;
    @Autowired SolicitudAclaracionFiscalizacionRepository aclaraciones;
    @Autowired EvidenciaFiscalizacionRepository evidencias;
    @Autowired ExpedienteHistorialRepository historial;
    @Autowired PasswordEncoder encoder;
    @Autowired FiscalizacionFileStorageService storage;

    private final List<String> temporaryAccounts = new ArrayList<>();
    private final Map<String,String> passwords = new HashMap<>();
    private final Map<String,String> usernames = new HashMap<>();

    @Test
    void continuaFixturePersistidoConJwtRealHastaCierre() throws Exception {
        List<ExpedienteFiscalizacion> marked = expedientes.findAll().stream().filter(e ->
                Objects.toString(e.getObservacionesIniciales(), "").contains(MARKER)
                        || Objects.toString(e.getDetalleOrigen(), "").contains(MARKER)).toList();
        Assumptions.assumeTrue(marked.size() == 1, "No hay un único fixture integrado ya persistido que continuar.");
        ExpedienteFiscalizacion fixture = marked.get(0);
        Assumptions.assumeFalse(fixture.getEstado() == EstadoExpediente.CERRADO,
                "El fixture ya está cerrado y validado; no se vuelve a operar ni duplicar.");

        try {
            reactivatePriorSyntheticReviewers(fixture.getId());
            createUser("f3b_admin", Rol.ADMIN_SISTEMA, "ADMIN_SISTEMA", null);
            createUser("f3b_municipio", Rol.MUNICIPIO, "MUNICIPIO", null);
            createUser("f3b_fiscal_propietario", Rol.FISCALIZADOR, "FISCALIZADOR", "FIS-001");
            createUser("f3b_fiscal_ajeno", Rol.FISCALIZADOR, "FISCALIZADOR", "FIS-002");
            String admin = login("f3b_admin"), municipality = login("f3b_municipio");
            String owner = login("f3b_fiscal_propietario"), other = login("f3b_fiscal_ajeno");

            String code = fixture.getCodigo(); long expedienteId = fixture.getId();
            LocalDate future = LocalDate.now(LIMA).plusDays(30);
            List<ProgramacionFiscalizacion> cycles = programaciones.findAllByExpedienteIdOrderByIdAsc(expedienteId);
            assertEquals(1, cycles.size(), "La continuación parte del único ciclo ya guardado.");
            long prog1 = cycles.get(0).getId();
            long dil1 = diligencias.findByProgramacionId(prog1).orElseThrow().getId();
            long rev1 = revisiones.findByDiligenciaId(dil1).orElseThrow().getId();
            List<SolicitudAclaracionFiscalizacion> clarifications = aclaraciones.findAllByRevisionIdOrderByFechaSolicitudAsc(rev1);
            assertEquals(1, clarifications.size()); long clarificationId = clarifications.get(0).getId();
            List<ActaFiscalizacion> firstActs = actas.findAllByDiligenciaExpedienteCodigoOrderByGeneradoEnAsc(code);
            assertEquals(1, firstActs.size()); long acta1 = firstActs.get(0).getId();
            long evidence1 = evidencias.findAllByDiligenciaIdOrderByFechaHoraAscIdAsc(dil1).get(0).getId();
            JsonNode actaBefore = body(call(HttpMethod.GET, "/api/actas/" + acta1, municipality, null), HttpStatus.OK);
            byte[] signedSnapshotBefore = raw(HttpMethod.GET, "/api/actas/" + acta1 + "/pdf", municipality, null, "application/pdf").getBody();
            byte[] evidenceBefore = raw(HttpMethod.GET, "/api/actas/" + acta1 + "/evidencias/" + evidence1, municipality, null, "image/png").getBody();

            assertStatus(call(HttpMethod.POST, "/api/revisiones/aclaraciones/" + clarificationId + "/responder", other,
                    Map.of("respuesta", "Intento desde fiscalizador ajeno.")), HttpStatus.FORBIDDEN);
            assertStatus(call(HttpMethod.PATCH, "/api/fiscalizador/diligencias/" + dil1, other,
                    Map.of("hechosConstatados", "Intento de editar una diligencia ajena.")), HttpStatus.FORBIDDEN);
            if (clarifications.get(0).getEstado() == EstadoAclaracion.PENDIENTE) {
                JsonNode inbox = body(call(HttpMethod.GET, "/api/revisiones/mis-aclaraciones", owner, null), HttpStatus.OK);
                assertTrue(contains(inbox, "id", clarificationId));
                body(call(HttpMethod.POST, "/api/revisiones/aclaraciones/" + clarificationId + "/responder", owner,
                        Map.of("respuesta", "Aclaración técnica sintética: la medición corresponde al equipo de prueba.")), HttpStatus.OK);
            }
            assertEquals(EstadoAclaracion.RESPONDIDA, aclaraciones.findById(clarificationId).orElseThrow().getEstado());
            assertNotNull(aclaraciones.findById(clarificationId).orElseThrow().getFechaRespuesta());
            assertEquals(actaBefore.path("contenido"), body(call(HttpMethod.GET, "/api/actas/" + acta1, municipality, null), HttpStatus.OK).path("contenido"));
            assertArrayEquals(signedSnapshotBefore, raw(HttpMethod.GET, "/api/actas/" + acta1 + "/pdf", municipality, null, "application/pdf").getBody());
            assertArrayEquals(evidenceBefore, raw(HttpMethod.GET, "/api/actas/" + acta1 + "/evidencias/" + evidence1, municipality, null, "image/png").getBody());
            JsonNode firstWorkspace = body(call(HttpMethod.GET, "/api/fiscalizador/programaciones/" + prog1 + "/diligencia", owner, null), HttpStatus.OK);
            assertEquals("Hechos sintéticos de primera diligencia sintética para " + MARKER, firstWorkspace.path("hechosConstatados").asText());
            assertEquals(1, firstWorkspace.path("evidencias").size());

            if (revisiones.findById(rev1).orElseThrow().getEstadoRevision() == EstadoRevision.REQUIERE_ACLARACION)
                body(call(HttpMethod.POST, "/api/revisiones/diligencias/" + dil1 + "/iniciar", municipality, null), HttpStatus.OK);
            JsonNode review1 = body(call(HttpMethod.GET, "/api/revisiones/" + rev1, municipality, null), HttpStatus.OK);
            assertEquals("EN_REVISION", review1.path("revision").path("estado").asText());

            // A real ADMIN token and field-fiscalizer token are denied by Spring Security's capabilities.
            assertStatus(call(HttpMethod.GET, "/api/revisiones/pendientes", admin, null), HttpStatus.FORBIDDEN);
            assertStatus(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/concluir", admin,
                    conclusion("CONFORMIDAD", "x", null, false, false, null)), HttpStatus.FORBIDDEN);
            assertStatus(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/concluir", owner,
                    conclusion("CONFORMIDAD", "x", null, false, false, null)), HttpStatus.FORBIDDEN);
            assertStatus(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/seguimientos", owner, followup(future)), HttpStatus.FORBIDDEN);
            assertStatus(call(HttpMethod.POST, "/api/fiscalizador/programaciones/" + prog1 + "/diligencia/iniciar", admin, Map.of()), HttpStatus.FORBIDDEN);

            if (revisiones.findById(rev1).orElseThrow().getEstadoRevision() != EstadoRevision.FINALIZADA)
                body(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/concluir", municipality,
                        conclusion("RECOMENDACION_MEJORAS_CORRECCIONES", "Reforzar el control preventivo observado en la inspección sintética.",
                                "Implementar registro diario de comprobación preventiva.", true, false, "Plazo técnico: 30 días")), HttpStatus.OK);
            JsonNode review1Final = body(call(HttpMethod.GET, "/api/revisiones/" + rev1, municipality, null), HttpStatus.OK);
            assertEquals("FINALIZADA", review1Final.path("revision").path("estado").asText());
            assertEquals("RECOMENDACION_MEJORAS_CORRECCIONES", review1Final.path("revision").path("conclusion").asText());
            assertTrue(review1Final.path("revision").path("requiereSeguimiento").asBoolean());
            assertEquals("EN_SEGUIMIENTO", body(call(HttpMethod.GET, "/api/expedientes/" + code, municipality, null), HttpStatus.OK).path("estado").asText());

            JsonNode followups = review1Final.path("revision").path("programacionSeguimiento");
            if (followups.isEmpty()) {
                body(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/seguimientos", municipality, followup(future)), HttpStatus.OK);
                review1Final = body(call(HttpMethod.GET, "/api/revisiones/" + rev1, municipality, null), HttpStatus.OK);
                followups = review1Final.path("revision").path("programacionSeguimiento");
            }
            assertEquals(1, followups.size());
            long prog2 = followups.get(0).path("id").asLong();
            assertEquals("VALIDACION_REVISION_FASE3B_INTEGRADA: verificar implementación de mejora", followups.get(0).path("motivo").asText());
            assertEquals(rev1, jdbc.queryForObject("SELECT revision_origen_id FROM programacion_fiscalizacion WHERE id=?", Long.class, prog2));
            assertEquals(expedienteId, jdbc.queryForObject("SELECT expediente_id FROM programacion_fiscalizacion WHERE id=?", Long.class, prog2));
            assertStatus(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/seguimientos", municipality, followup(future.plusDays(1))), HttpStatus.CONFLICT);

            Optional<DiligenciaFiscalizacion> saved2 = diligencias.findByProgramacionId(prog2);
            long dil2 = saved2.map(DiligenciaFiscalizacion::getId).orElseGet(() -> runDiligence(prog2, "segunda diligencia sintética", owner, other));
            var revision2Saved = revisiones.findByDiligenciaId(dil2).orElseThrow(); long rev2 = revision2Saved.getId();
            JsonNode acts = body(call(HttpMethod.GET, "/api/actas/expediente/" + code, municipality, null), HttpStatus.OK);
            assertEquals(2, acts.size()); long acta2 = acts.get(1).path("id").asLong();
            if (revision2Saved.getEstadoRevision() == EstadoRevision.PENDIENTE)
                body(call(HttpMethod.POST, "/api/revisiones/diligencias/" + dil2 + "/iniciar", municipality, null), HttpStatus.OK);
            if (revisiones.findById(rev2).orElseThrow().getEstadoRevision() != EstadoRevision.FINALIZADA)
                body(call(HttpMethod.POST, "/api/revisiones/" + rev2 + "/concluir", municipality,
                        conclusion("CONFORMIDAD", "La verificación sintética confirma la implementación de la mejora acordada.", null, false, false, null)), HttpStatus.OK);
            JsonNode review2 = body(call(HttpMethod.GET, "/api/revisiones/" + rev2, municipality, null), HttpStatus.OK);
            assertEquals("CONFORMIDAD", review2.path("revision").path("conclusion").asText());
            assertFalse(review2.path("revision").path("requiereSeguimiento").asBoolean());
            assertEquals("CERRADO", body(call(HttpMethod.GET, "/api/expedientes/" + code, municipality, null), HttpStatus.OK).path("estado").asText());
            assertFalse(aclaraciones.existsByRevisionIdAndEstado(rev2, EstadoAclaracion.PENDIENTE));

            // Check all ordinary post-close mutations use the implemented 409 protection.
            assertStatus(call(HttpMethod.POST, "/api/expedientes/" + code + "/programaciones", municipality,
                    Map.of("fiscalizadorId", "FIS-001", "fecha", future.plusDays(4).toString(), "hora", "12:00", "tipoVisita", "PROGRAMADA")), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.POST, "/api/programaciones/" + prog2 + "/reprogramar", municipality,
                    Map.of("fecha", future.plusDays(5).toString(), "hora", "12:00", "motivo", "Prueba de bloqueo", "tipoVisita", "PROGRAMADA")), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.POST, "/api/programaciones/" + prog2 + "/cancelar", municipality, Map.of("motivo", "Prueba de bloqueo")), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.PUT, "/api/expedientes/" + code, municipality, expedienteRequest("Cambio posterior al cierre")), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.POST, "/api/revisiones/diligencias/" + dil2 + "/iniciar", municipality, null), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.POST, "/api/revisiones/" + rev2 + "/concluir", municipality,
                    conclusion("CONFORMIDAD", "Intento posterior al cierre", null, false, false, null)), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.POST, "/api/fiscalizador/programaciones/" + prog2 + "/diligencia/iniciar", owner, Map.of()), HttpStatus.CONFLICT);

            RevisionFiscalizacion finalRevision = revisiones.findById(rev2).orElseThrow();
            assertNotNull(finalRevision.getInformePdfNombre()); assertNotNull(finalRevision.getInformePdfInterno()); assertNotNull(finalRevision.getInformeChecksum());
            var persistedReport = storage.load(finalRevision.getInformePdfInterno()); assertNotNull(persistedReport); assertTrue(persistedReport.exists());
            byte[] reportBytes = persistedReport.getInputStream().readAllBytes(); assertPdf(reportBytes);
            assertEquals(finalRevision.getInformeChecksum(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(reportBytes)));
            ResponseEntity<byte[]> downloaded = http.exchange("/api/revisiones/" + rev2 + "/informe.pdf", HttpMethod.GET,
                    new HttpEntity<>(headers(municipality)), byte[].class);
            assertEquals(HttpStatus.OK, downloaded.getStatusCode()); assertEquals("application/pdf", downloaded.getHeaders().getContentType().toString());
            assertArrayEquals(reportBytes, downloaded.getBody());
            String pdf = new String(downloaded.getBody(), StandardCharsets.ISO_8859_1);
            for (String text : List.of("MUNICIPALIDAD DISTRITAL DE SAN MIGUEL", code, "INFORME DE CONCLUSION", "ACT-", "Revisor:",
                    "Conclusion: CONFORMIDAD", "Fundamento:", "Seguimiento requerido: false", "Fecha:")) assertTrue(pdf.contains(text), "PDF sin " + text);
            assertFalse(pdf.toLowerCase(Locale.ROOT).contains("multa"));
            assertTrue(pdf.contains("no determina responsabilidad ni impone sancion"));

            List<ProgramacionFiscalizacion> cycleRows = programaciones.findAllByExpedienteIdOrderByIdAsc(expedienteId);
            assertEquals(2, cycleRows.size()); assertEquals(2, diligencias.findAllByExpedienteIdOrderByFechaInicioDesc(expedienteId).size());
            assertEquals(2, actas.findAllByDiligenciaExpedienteCodigoOrderByGeneradoEnAsc(code).size());
            assertEquals(2, revisiones.findAllByExpedienteCodigoOrderByCreadoEnAsc(code).size());
            assertEquals(1, aclaraciones.findAllByRevisionIdOrderByFechaSolicitudAsc(rev1).size());
            List<ExpedienteHistorial> events = historial.findAllByExpedienteIdOrderByFechaHoraAscIdAsc(expedienteId);
            assertTrue(events.size() >= 15); assertTrue(events.stream().allMatch(e -> e.getFechaHora() != null && e.getActorUsername() != null));
            String types = String.join(" ", events.stream().map(ExpedienteHistorial::getTipoEvento).toList());
            for (String type : List.of("EXPEDIENTE_CREADO", "PROGRAMACION_CREADA", "DILIGENCIA_INICIADA", "DILIGENCIA_FINALIZADA", "ACTA_GENERADA",
                    "REVISION_INICIADA", "ACLARACION_SOLICITADA", "ACLARACION_RESPONDIDA", "REVISION_REANUDADA", "CONCLUSION_REGISTRADA",
                    "SEGUIMIENTO_REQUERIDO", "PROGRAMACION_SEGUIMIENTO_CREADA", "INFORME_CONCLUSION_GENERADO", "EXPEDIENTE_CERRADO"))
                assertTrue(types.contains(type), "Historial sin " + type);
            assertEquals("FINALIZADA", revisiones.findById(rev1).orElseThrow().getEstadoRevision().name());
            assertEquals("FINALIZADA", finalRevision.getEstadoRevision().name());
            assertEquals(EstadoProgramacion.REALIZADA, programaciones.findById(prog1).orElseThrow().getEstadoProgramacion());
            assertEquals(EstadoProgramacion.REALIZADA, programaciones.findById(prog2).orElseThrow().getEstadoProgramacion());
            List<String> forbiddenTableNames = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name REGEXP 'multa|sancion|procedimiento|deuda|responsabilidad|resolucion'", String.class);
            assertTrue(forbiddenTableNames.isEmpty(), "No debe crearse un módulo o tabla sancionadora automáticamente: " + forbiddenTableNames);
            assertEquals(2, acts.size()); assertTrue(acta2 > acta1);
        } finally {
            deactivateTemporaryAccounts();
        }
    }

    private long runDiligence(long programmingId, String cycle, String ownerToken, String otherToken) {
        assertStatus(call(HttpMethod.GET, "/api/fiscalizador/programaciones/" + programmingId + "/diligencia", otherToken, null), HttpStatus.FORBIDDEN);
        JsonNode started = body(call(HttpMethod.POST, "/api/fiscalizador/programaciones/" + programmingId + "/diligencia/iniciar", ownerToken,
                Map.of("estadoGps", "DISPONIBLE", "latitud", -12.09, "longitud", -77.08)), HttpStatus.OK);
        long id = started.path("id").asLong();
        assertStatus(call(HttpMethod.PATCH, "/api/fiscalizador/diligencias/" + id, otherToken, Map.of("hechosConstatados", "Edición ajena")), HttpStatus.FORBIDDEN);
        body(call(HttpMethod.PATCH, "/api/fiscalizador/diligencias/" + id, ownerToken,
                Map.of("situacionPersona", "IDENTIFICADA", "personaNombres", "Persona", "personaApellidos", "Ficticia",
                        "personaTipoDocumento", "DNI", "personaNumeroDocumento", "00000000", "personaCondicion", "Responsable de prueba",
                        "personaObservaciones", "Identidad ficticia", "hechosConstatados", "Hechos sintéticos de " + cycle + " para " + MARKER,
                        "ocurrencias", "Sin incidencias reales.", "observacionesFiscalizador", "Registro técnico.")), HttpStatus.OK);
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/mO8AAAAASUVORK5CYII=");
        ByteArrayResource resource = new ByteArrayResource(png) { @Override public String getFilename() { return "evidencia-sintetica.png"; } };
        HttpHeaders fileHeaders = new HttpHeaders(); fileHeaders.setContentType(MediaType.IMAGE_PNG);
        MultiValueMap<String,Object> form = new LinkedMultiValueMap<>(); form.add("tipo", "FOTO"); form.add("descripcion", MARKER + " " + cycle);
        form.add("archivo", new HttpEntity<>(resource, fileHeaders));
        HttpHeaders multipart = headers(ownerToken); multipart.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<JsonNode> evidence = http.exchange("/api/fiscalizador/diligencias/" + id + "/evidencias", HttpMethod.POST,
                new HttpEntity<>(form, multipart), JsonNode.class);
        assertEquals(HttpStatus.OK, evidence.getStatusCode());
        body(call(HttpMethod.POST, "/api/fiscalizador/diligencias/" + id + "/finalizar", ownerToken,
                Map.of("firmaFiscalizado", "FIRMA_PENDIENTE", "copiaEntregada", false, "medioEntrega", "PENDIENTE", "estadoGpsCierre", "DISPONIBLE",
                        "latitudCierre", -12.09, "longitudCierre", -77.08)), HttpStatus.OK);
        return id;
    }

    private Object conclusion(String type, String basis, String description, boolean followup, boolean referral, String deadline) {
        Map<String,Object> payload = new LinkedHashMap<>(); payload.put("conclusion", type); payload.put("fundamentoConclusion", basis);
        payload.put("descripcionConclusion", description); payload.put("requiereSeguimiento", followup); payload.put("requiereDerivacion", referral);
        payload.put("plazoConclusion", deadline); return payload;
    }
    private Object followup(LocalDate date) {
        return Map.of("fecha", date.toString(), "hora", "11:00", "tipoVisita", "PROGRAMADA", "fiscalizadorId", "FIS-001",
                "motivo", MARKER + ": verificar implementación de mejora");
    }
    private Object expedienteRequest(String notes) {
        return Map.ofEntries(Map.entry("origen","INICIATIVA_MUNICIPAL"), Map.entry("detalleOrigen",MARKER),
                Map.entry("objetoFiscalizacion","Inspección sintética de condiciones de seguridad"), Map.entry("observacionesIniciales",notes),
                Map.entry("tipoAdministrado","PERSONA_NATURAL"), Map.entry("documentoAdministrado","00000000"), Map.entry("nombreAdministrado","Persona Ficticia"),
                Map.entry("administradoApellidos","Prueba Integrada"), Map.entry("telefonoContacto","900000000"), Map.entry("direccionFiscalizada","Calle Sintética 123"),
                Map.entry("referenciaDomicilio","Fixture técnico"), Map.entry("latitud",-12.09), Map.entry("longitud",-77.08));
    }
    private void createUser(String prefix, Rol legacyRole, String roleName, String fiscalizadorId) {
        String username = prefix + "_" + UUID.randomUUID().toString().substring(0,8); String password = UUID.randomUUID() + "-T3mp!";
        AppUser user = new AppUser(); user.setUsername(username); user.setPasswordHash(encoder.encode(password)); user.setDisplayName("Fixture técnico " + roleName);
        user.setCargo("Validación Fase 3B"); user.setRole(legacyRole); user.getRoles().add(roles.findByName(roleName).orElseThrow());
        user.setActivo(true); user.setCreatedAt(LocalDateTime.now(LIMA));
        if (fiscalizadorId != null) user.setFiscalizador(fiscalizadores.findById(fiscalizadorId).orElseThrow());
        users.saveAndFlush(user); temporaryAccounts.add(username); passwords.put(prefix, password); usernames.put(prefix, username);
    }
    private void reactivatePriorSyntheticReviewers(long expedienteId) {
        List<String> reviewers = jdbc.queryForList("SELECT DISTINCT u.username FROM revision_fiscalizacion r JOIN app_user u ON u.id=r.revisor_id WHERE r.expediente_id=?", String.class, expedienteId);
        for (String username : reviewers) {
            if (!username.startsWith("f3b_municipio_")) continue;
            AppUser prior = users.findByUsernameIgnoreCase(username).orElseThrow();
            prior.setPasswordHash(encoder.encode(UUID.randomUUID() + "-T3mp!")); prior.setActivo(true);
            users.saveAndFlush(prior); temporaryAccounts.add(username);
        }
    }
    private String login(String prefix) {
        String username = usernames.get(prefix);
        JsonNode response = body(call(HttpMethod.POST, "/api/auth/login", null, Map.of("usuario",username,"password",passwords.get(prefix))), HttpStatus.OK);
        String jwt = response.path("token").asText(); assertFalse(jwt.isBlank()); return jwt;
    }
    private void deactivateTemporaryAccounts() {
        for (String username : temporaryAccounts) users.findByUsernameIgnoreCase(username).ifPresent(u -> { u.setActivo(false); users.save(u); });
    }
    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object payload) {
        return http.exchange(path, method, new HttpEntity<>(payload, headers(token)), JsonNode.class);
    }
    private ResponseEntity<byte[]> raw(HttpMethod method, String path, String token, Object payload, String expectedMime) {
        ResponseEntity<byte[]> response = http.exchange(path, method, new HttpEntity<>(payload, headers(token)), byte[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode()); assertEquals(expectedMime, response.getHeaders().getContentType().toString());
        assertNotNull(response.getBody()); return response;
    }
    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); if (token != null) headers.setBearerAuth(token); return headers;
    }
    private JsonNode body(ResponseEntity<JsonNode> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode(), () -> "HTTP " + response.getStatusCode() + " body=" + response.getBody());
        assertNotNull(response.getBody()); return response.getBody();
    }
    private void assertStatus(ResponseEntity<JsonNode> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode(), () -> "HTTP " + response.getStatusCode() + " body=" + response.getBody());
    }
    private boolean contains(JsonNode array, String field, long value) {
        for (JsonNode item : array) if (item.path(field).asLong(Long.MIN_VALUE) == value) return true; return false;
    }
    private void assertPdf(byte[] bytes) {
        assertNotNull(bytes); assertTrue(bytes.length > 0); assertTrue(new String(bytes,0,5,StandardCharsets.US_ASCII).startsWith("%PDF-"));
    }
}
