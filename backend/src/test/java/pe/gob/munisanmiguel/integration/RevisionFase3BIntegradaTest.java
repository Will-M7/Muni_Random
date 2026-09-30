package pe.gob.munisanmiguel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
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

/** Real HTTP/JWT integration fixture. It intentionally leaves the expediente as development evidence. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.storage.diligencias-path=target/fase3b-integrada/diligencias",
        "app.storage.path=target/fase3b-integrada/solicitudes"
})
class RevisionFase3BIntegradaTest {
    private static final String MARCA = "VALIDACION_REVISION_FASE3B_INTEGRADA";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
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
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired FiscalizacionFileStorageService storage;

    private final List<String> cuentasTemporales = new ArrayList<>();
    private String adminToken;
    private String municipioToken;
    private String fiscalToken;
    private String otroFiscalToken;

    @Test
    void recorridoIntegradoConJwtRealDejaExpedienteCerradoYVerificable() throws Exception {
        // The marker check prevents a rerun from creating a duplicate fixture.
        boolean yaExiste = expedientes.findAll().stream().anyMatch(e ->
                Objects.toString(e.getObservacionesIniciales(), "").contains(MARCA)
                        || Objects.toString(e.getObjetoFiscalizacion(), "").contains(MARCA));
        Assumptions.assumeFalse(yaExiste, "El fixture ya existe; no se crea un segundo expediente.");

        try {
            crearCuentasTemporales();
            adminToken = login("f3b_admin", passwordFor("f3b_admin"));
            municipioToken = login("f3b_municipio", passwordFor("f3b_municipio"));
            fiscalToken = login("f3b_fiscal_propietario", passwordFor("f3b_fiscal_propietario"));
            otroFiscalToken = login("f3b_fiscal_ajeno", passwordFor("f3b_fiscal_ajeno"));
            assertStatus(call(HttpMethod.GET, "/api/revisiones/pendientes", null, null), HttpStatus.UNAUTHORIZED);

            LocalDate fecha = LocalDate.now(LIMA).plusDays(20);
            JsonNode expediente = body(call(HttpMethod.POST, "/api/expedientes", municipioToken, expedienteRequest("VALIDACION_REVISION_FASE3B_INTEGRADA")), HttpStatus.CREATED);
            String codigo = expediente.path("codigo").asText();
            Long expedienteId = expediente.path("id").asLong();
            assertTrue(expedienteId > 0);
            assertEquals("ABIERTO", expediente.path("estado").asText());

            JsonNode programacion1 = body(call(HttpMethod.POST, "/api/expedientes/" + codigo + "/programaciones", municipioToken,
                    Map.of("fiscalizadorId", "FIS-001", "fecha", fecha.toString(), "hora", "10:00", "tipoVisita", "PROGRAMADA")), HttpStatus.CREATED);
            long prog1 = programacion1.path("id").asLong();
            long dil1 = ejecutarDiligencia(prog1, "primera diligencia sintética", fiscalToken, otroFiscalToken);
            JsonNode acta1 = first(body(call(HttpMethod.GET, "/api/actas/expediente/" + codigo, municipioToken, null), HttpStatus.OK));
            long acta1Id = acta1.path("id").asLong();
            long evidencia1 = evidencias.findAllByDiligenciaIdOrderByFechaHoraAscIdAsc(dil1).get(0).getId();
            JsonNode acta1Antes = body(call(HttpMethod.GET, "/api/actas/" + acta1Id, municipioToken, null), HttpStatus.OK);
            byte[] acta1Pdf = binary(raw(HttpMethod.GET, "/api/actas/" + acta1Id + "/pdf", municipioToken, null), "application/pdf");
            assertPdf(acta1Pdf);
            byte[] evidencia1Descargada = binary(raw(HttpMethod.GET, "/api/actas/" + acta1Id + "/evidencias/" + evidencia1, municipioToken, null), "image/png");
            assertTrue(evidencia1Descargada.length > 0);
            assertEquals("PENDIENTE_REVISION", body(call(HttpMethod.GET, "/api/expedientes/" + codigo, municipioToken, null), HttpStatus.OK).path("estado").asText());

            JsonNode pendientes = body(call(HttpMethod.GET, "/api/revisiones/pendientes", municipioToken, null), HttpStatus.OK);
            assertTrue(contains(pendientes, "diligenciaId", dil1));
            JsonNode rev1Detalle = body(call(HttpMethod.POST, "/api/revisiones/diligencias/" + dil1 + "/iniciar", municipioToken, null), HttpStatus.OK);
            long rev1 = rev1Detalle.path("revision").path("id").asLong();
            assertEquals("EN_REVISION", rev1Detalle.path("revision").path("estado").asText());
            assertTrue(rev1Detalle.path("acta").path("contenido").has("hechosConstatados"));
            assertEquals(1, rev1Detalle.path("evidencias").size());

            // Owner-only response is checked with real JWTs before the owner answers.
            JsonNode aclaracion = body(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/aclaraciones", municipioToken,
                    Map.of("mensaje", "Aclarar el sustento sintético de la observación registrada.")), HttpStatus.OK);
            long aclaracionId = aclaracion.path("id").asLong();
            assertEquals("PENDIENTE", aclaracion.path("estado").asText());
            assertEquals("REQUIERE_ACLARACION", revisiones.findById(rev1).orElseThrow().getEstadoRevision().name());
            assertNotNull(aclaraciones.findById(aclaracionId).orElseThrow().getFechaSolicitud());
            assertTrue(historial.findAllByExpedienteIdOrderByFechaHoraAscIdAsc(expedienteId).stream()
                    .filter(h -> h.getTipoEvento().equals("ACLARACION_SOLICITADA"))
                    .anyMatch(h -> h.getActorUsername().startsWith("f3b_municipio_")));
            assertStatus(call(HttpMethod.POST, "/api/revisiones/aclaraciones/" + aclaracionId + "/responder", otroFiscalToken,
                    Map.of("respuesta", "Intento desde fiscalizador no propietario.")), HttpStatus.FORBIDDEN);
            assertStatus(call(HttpMethod.PATCH, "/api/fiscalizador/diligencias/" + dil1, otroFiscalToken,
                    Map.of("hechosConstatados", "Edición ajena no permitida.")), HttpStatus.FORBIDDEN);
            JsonNode pendientesFisc = body(call(HttpMethod.GET, "/api/revisiones/mis-aclaraciones", fiscalToken, null), HttpStatus.OK);
            assertTrue(contains(pendientesFisc, "id", aclaracionId));
            JsonNode aclaracionRespondida = body(call(HttpMethod.POST, "/api/revisiones/aclaraciones/" + aclaracionId + "/responder", fiscalToken,
                    Map.of("respuesta", "Aclaración técnica sintética: la medición corresponde al equipo de prueba.")), HttpStatus.OK);
            assertEquals("RESPONDIDA", aclaracionRespondida.path("estado").asText());
            assertNotNull(aclaraciones.findById(aclaracionId).orElseThrow().getFechaRespuesta());
            JsonNode acta1Despues = body(call(HttpMethod.GET, "/api/actas/" + acta1Id, municipioToken, null), HttpStatus.OK);
            assertEquals(acta1Antes.path("contenido"), acta1Despues.path("contenido"));
            JsonNode espacio1 = body(call(HttpMethod.GET, "/api/fiscalizador/programaciones/" + prog1 + "/diligencia", fiscalToken, null), HttpStatus.OK);
            assertEquals("Hechos sintéticos de primera visita para VALIDACION_REVISION_FASE3B_INTEGRADA", espacio1.path("hechosConstatados").asText());
            assertEquals(1, espacio1.path("evidencias").size());

            JsonNode retomada = body(call(HttpMethod.POST, "/api/revisiones/diligencias/" + dil1 + "/iniciar", municipioToken, null), HttpStatus.OK);
            assertEquals("EN_REVISION", retomada.path("revision").path("estado").asText());

            // ADMIN and fiscalizador roles cannot use review-only operations even with valid JWTs.
            assertStatus(call(HttpMethod.GET, "/api/revisiones/pendientes", adminToken, null), HttpStatus.FORBIDDEN);
            assertStatus(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/concluir", adminToken, conclusion("CONFORMIDAD", "x", null, false, false, null)), HttpStatus.FORBIDDEN);
            assertStatus(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/concluir", fiscalToken, conclusion("CONFORMIDAD", "x", null, false, false, null)), HttpStatus.FORBIDDEN);
            assertStatus(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/seguimientos", fiscalToken, seguimiento(fecha.plusDays(4), "11:00")), HttpStatus.FORBIDDEN);
            assertStatus(call(HttpMethod.POST, "/api/fiscalizador/programaciones/" + prog1 + "/diligencia/iniciar", adminToken, Map.of()), HttpStatus.FORBIDDEN);

            JsonNode cierreRevision1 = body(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/concluir", municipioToken,
                    conclusion("RECOMENDACION_MEJORAS_CORRECCIONES", "Reforzar el control preventivo observado en la inspección sintética.",
                            "Implementar registro diario de comprobación preventiva.", true, false, "Plazo técnico: 30 días")), HttpStatus.OK);
            assertEquals("FINALIZADA", cierreRevision1.path("revision").path("estado").asText());
            assertEquals("RECOMENDACION_MEJORAS_CORRECCIONES", cierreRevision1.path("revision").path("conclusion").asText());
            assertTrue(cierreRevision1.path("revision").path("requiereSeguimiento").asBoolean());
            assertEquals("EN_SEGUIMIENTO", body(call(HttpMethod.GET, "/api/expedientes/" + codigo, municipioToken, null), HttpStatus.OK).path("estado").asText());

            JsonNode seguimiento = body(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/seguimientos", municipioToken,
                    seguimiento(fecha.plusDays(4), "11:00")), HttpStatus.OK);
            long prog2 = seguimiento.path("id").asLong();
            assertEquals(codigo, seguimiento.path("expedienteCodigo").asText());
            assertEquals("VALIDACION_REVISION_FASE3B_INTEGRADA: verificar implementación de mejora", seguimiento.path("motivo").asText());
            assertEquals(rev1, jdbc.queryForObject("SELECT revision_origen_id FROM programacion_fiscalizacion WHERE id=?", Long.class, prog2));
            assertStatus(call(HttpMethod.POST, "/api/revisiones/" + rev1 + "/seguimientos", municipioToken,
                    seguimiento(fecha.plusDays(5), "11:30")), HttpStatus.CONFLICT);

            long dil2 = ejecutarDiligencia(prog2, "segunda diligencia sintética", fiscalToken, otroFiscalToken);
            JsonNode actasTrasSegundoCiclo = body(call(HttpMethod.GET, "/api/actas/expediente/" + codigo, municipioToken, null), HttpStatus.OK);
            assertEquals(2, actasTrasSegundoCiclo.size());
            long acta2Id = actasTrasSegundoCiclo.get(1).path("id").asLong();
            JsonNode rev2Detalle = body(call(HttpMethod.POST, "/api/revisiones/diligencias/" + dil2 + "/iniciar", municipioToken, null), HttpStatus.OK);
            long rev2 = rev2Detalle.path("revision").path("id").asLong();
            JsonNode conformidad = body(call(HttpMethod.POST, "/api/revisiones/" + rev2 + "/concluir", municipioToken,
                    conclusion("CONFORMIDAD", "La verificación sintética confirma la implementación de la mejora acordada.", null, false, false, null)), HttpStatus.OK);
            assertEquals("FINALIZADA", conformidad.path("revision").path("estado").asText());
            assertFalse(conformidad.path("revision").path("requiereSeguimiento").asBoolean());
            assertFalse(aclaraciones.existsByRevisionIdAndEstado(rev2, EstadoAclaracion.PENDIENTE));
            assertFalse(programaciones.findAllByExpedienteIdOrderByIdAsc(expedienteId).stream()
                    .anyMatch(p -> p.getEstadoProgramacion() == EstadoProgramacion.PROGRAMADA || p.getEstadoProgramacion() == EstadoProgramacion.EN_CURSO));

            JsonNode expedienteCerrado = body(call(HttpMethod.GET, "/api/expedientes/" + codigo, municipioToken, null), HttpStatus.OK);
            assertEquals("CERRADO", expedienteCerrado.path("estado").asText());
            // Closed expedientes reject ordinary mutations and attempts to restart a completed review.
            assertStatus(call(HttpMethod.POST, "/api/expedientes/" + codigo + "/programaciones", municipioToken,
                    Map.of("fiscalizadorId", "FIS-001", "fecha", fecha.plusDays(6).toString(), "hora", "12:00", "tipoVisita", "PROGRAMADA")), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.POST, "/api/programaciones/" + prog2 + "/reprogramar", municipioToken,
                    Map.of("fecha", fecha.plusDays(7).toString(), "hora", "12:00", "motivo", "Prueba de bloqueo", "tipoVisita", "PROGRAMADA")), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.POST, "/api/programaciones/" + prog2 + "/cancelar", municipioToken,
                    Map.of("motivo", "Prueba de bloqueo")), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.PUT, "/api/expedientes/" + codigo, municipioToken, expedienteRequest("Cambio posterior al cierre")), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.POST, "/api/revisiones/diligencias/" + dil2 + "/iniciar", municipioToken, null), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.POST, "/api/revisiones/" + rev2 + "/concluir", municipioToken,
                    conclusion("CONFORMIDAD", "Intento posterior al cierre", null, false, false, null)), HttpStatus.CONFLICT);
            assertStatus(call(HttpMethod.POST, "/api/fiscalizador/programaciones/" + prog2 + "/diligencia/iniciar", fiscalToken, Map.of()), HttpStatus.CONFLICT);

            // Persisted/downloadable conclusion report and integrity metadata.
            RevisionFiscalizacion revisionFinal = revisiones.findById(rev2).orElseThrow();
            assertNotNull(revisionFinal.getInformePdfInterno());
            assertNotNull(revisionFinal.getInformePdfNombre());
            assertNotNull(revisionFinal.getInformeChecksum());
            var stored = storage.load(revisionFinal.getInformePdfInterno());
            assertNotNull(stored);
            assertTrue(stored.exists());
            byte[] persistedPdf = stored.getInputStream().readAllBytes();
            assertTrue(persistedPdf.length > 0);
            assertEquals(revisionFinal.getInformeChecksum(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(persistedPdf)));
            ResponseEntity<byte[]> informe = raw(HttpMethod.GET, "/api/revisiones/" + rev2 + "/informe.pdf", municipioToken, null);
            assertEquals(HttpStatus.OK, informe.getStatusCode());
            assertEquals("application/pdf", informe.getHeaders().getContentType().toString());
            assertPdf(informe.getBody());
            String pdfText = new String(informe.getBody(), StandardCharsets.ISO_8859_1);
            for (String texto : List.of("MUNICIPALIDAD DISTRITAL DE SAN MIGUEL", codigo, "INFORME DE CONCLUSION", "ACT-", "Revisor:", "Conclusion: CONFORMIDAD", "Fundamento:", "Seguimiento requerido: false", "Fecha:"))
                assertTrue(pdfText.contains(texto), "PDF sin texto: " + texto);
            assertFalse(pdfText.toLowerCase(Locale.ROOT).contains("multa"));
            assertTrue(pdfText.contains("no determina responsabilidad ni impone sancion"));

            // Stored cycle counts and chronological event evidence.
            assertEquals(2, programaciones.findAllByExpedienteIdOrderByIdAsc(expedienteId).size());
            assertEquals(2, diligencias.findAllByExpedienteIdOrderByFechaInicioDesc(expedienteId).size());
            assertEquals(2, actas.findAllByDiligenciaExpedienteCodigoOrderByGeneradoEnAsc(codigo).size());
            assertEquals(2, revisiones.findAllByExpedienteCodigoOrderByCreadoEnAsc(codigo).size());
            assertEquals(1, aclaraciones.findAllByRevisionIdOrderByFechaSolicitudAsc(rev1).size());
            List<ExpedienteHistorial> eventos = historial.findAllByExpedienteIdOrderByFechaHoraAscIdAsc(expedienteId);
            assertTrue(eventos.size() >= 15);
            assertTrue(eventos.stream().allMatch(h -> h.getFechaHora() != null && h.getActorUsername() != null));
            String secuencia = String.join(" ", eventos.stream().map(ExpedienteHistorial::getTipoEvento).toList());
            for (String evento : List.of("EXPEDIENTE_CREADO", "PROGRAMACION_CREADA", "DILIGENCIA_INICIADA", "DILIGENCIA_FINALIZADA",
                    "ACTA_GENERADA", "REVISION_INICIADA", "ACLARACION_SOLICITADA", "ACLARACION_RESPONDIDA", "REVISION_REANUDADA",
                    "CONCLUSION_REGISTRADA", "SEGUIMIENTO_REQUERIDO", "PROGRAMACION_SEGUIMIENTO_CREADA", "INFORME_CONCLUSION_GENERADO", "EXPEDIENTE_CERRADO"))
                assertTrue(secuencia.contains(evento), "Historial sin evento " + evento);

            // No multa/procedure/debt/resolution or liability tables are written by conclusion flow.
            assertTrue(revisionFinal.isRequiereDerivacion() == false);
            assertTrue(revisionFinal.getConclusion() == ConclusionFiscalizacion.CONFORMIDAD);
            assertEquals(2, actasTrasSegundoCiclo.size());
        } finally {
            desactivarCuentasTemporales();
        }
    }

    private long ejecutarDiligencia(long programacion, String ciclo, String propietario, String ajeno) throws Exception {
        assertStatus(call(HttpMethod.GET, "/api/fiscalizador/programaciones/" + programacion + "/diligencia", ajeno, null), HttpStatus.FORBIDDEN);
        JsonNode iniciado = body(call(HttpMethod.POST, "/api/fiscalizador/programaciones/" + programacion + "/diligencia/iniciar", propietario,
                Map.of("estadoGps", "DISPONIBLE", "latitud", -12.09, "longitud", -77.08)), HttpStatus.OK);
        long diligencia = iniciado.path("id").asLong();
        assertTrue(diligencia > 0);
        assertStatus(call(HttpMethod.PATCH, "/api/fiscalizador/diligencias/" + diligencia, ajeno,
                Map.of("hechosConstatados", "Edición ajena denegada.")), HttpStatus.FORBIDDEN);
        body(call(HttpMethod.PATCH, "/api/fiscalizador/diligencias/" + diligencia, propietario,
                Map.of("situacionPersona", "IDENTIFICADA", "personaNombres", "Persona", "personaApellidos", "Ficticia",
                        "personaTipoDocumento", "DNI", "personaNumeroDocumento", "00000000", "personaCondicion", "Responsable de prueba",
                        "personaObservaciones", "Identidad ficticia de fixture", "hechosConstatados", "Hechos sintéticos de " + ciclo + " para " + MARCA,
                        "ocurrencias", "Sin incidencias reales.", "observacionesFiscalizador", "Registro exclusivamente técnico.")), HttpStatus.OK);
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_PNG);
        ByteArrayResource png = new ByteArrayResource(Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/mO8AAAAASUVORK5CYII=")) {
            @Override public String getFilename() { return "evidencia-sintetica.png"; }
        };
        MultiValueMap<String,Object> form = new LinkedMultiValueMap<>();
        form.add("tipo", "FOTO"); form.add("descripcion", MARCA + " " + ciclo);
        HttpEntity<ByteArrayResource> file = new HttpEntity<>(png, fileHeaders);
        form.add("archivo", file);
        HttpHeaders multipartHeaders = new HttpHeaders(); multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        JsonNode evidencia = body(http.exchange("/api/fiscalizador/diligencias/" + diligencia + "/evidencias", HttpMethod.POST,
                entity(form, propietario), JsonNode.class), HttpStatus.OK);
        assertEquals("FOTO", evidencia.path("tipo").asText());
        JsonNode finalizada = body(call(HttpMethod.POST, "/api/fiscalizador/diligencias/" + diligencia + "/finalizar", propietario,
                Map.of("firmaFiscalizado", "FIRMA_PENDIENTE", "copiaEntregada", false, "medioEntrega", "PENDIENTE", "estadoGpsCierre", "DISPONIBLE",
                        "latitudCierre", -12.09, "longitudCierre", -77.08)), HttpStatus.OK);
        assertTrue(finalizada.path("id").asLong() > 0);
        return diligencia;
    }

    private HttpEntity<?> entity(Object body, String token) {
        HttpHeaders headers = authHeaders(token);
        if (body instanceof MultiValueMap<?,?>) headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        else headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private void crearCuentasTemporales() {
        createUser("f3b_admin", Rol.ADMIN_SISTEMA, "ADMIN_SISTEMA", null);
        createUser("f3b_municipio", Rol.MUNICIPIO, "MUNICIPIO", null);
        createUser("f3b_fiscal_propietario", Rol.FISCALIZADOR, "FISCALIZADOR", "FIS-001");
        createUser("f3b_fiscal_ajeno", Rol.FISCALIZADOR, "FISCALIZADOR", "FIS-002");
    }

    private final Map<String,String> passwordByUsername = new HashMap<>();
    private String passwordFor(String username) { return passwordByUsername.get(username); }
    private void createUser(String username, Rol legacyRole, String roleName, String fiscalizadorId) {
        String full = username + "_" + UUID.randomUUID().toString().substring(0, 8);
        String password = UUID.randomUUID() + "-T3mp!";
        AppUser user = new AppUser(); user.setUsername(full); user.setPasswordHash(encoder.encode(password));
        user.setDisplayName("Fixture técnico " + roleName); user.setCargo("Validación Fase 3B"); user.setRole(legacyRole);
        user.getRoles().add(roles.findByName(roleName).orElseThrow()); user.setActivo(true); user.setCreatedAt(LocalDateTime.now(LIMA));
        if (fiscalizadorId != null) user.setFiscalizador(fiscalizadores.findById(fiscalizadorId).orElseThrow());
        users.saveAndFlush(user); cuentasTemporales.add(full); passwordByUsername.put(username, password);
    }

    private void desactivarCuentasTemporales() {
        for (String username : cuentasTemporales) users.findByUsernameIgnoreCase(username).ifPresent(u -> { u.setActivo(false); users.save(u); });
    }

    private String login(String username, String password) {
        String full = cuentasTemporales.stream().filter(u -> u.startsWith(username + "_")).findFirst().orElseThrow();
        JsonNode response = body(call(HttpMethod.POST, "/api/auth/login", null, Map.of("usuario", full, "password", password)), HttpStatus.OK);
        String token = response.path("token").asText(); assertFalse(token.isBlank()); return token;
    }

    private Object conclusion(String kind, String fundamento, String descripcion, boolean seguimiento, boolean derivacion, String plazo) {
        Map<String,Object> m = new LinkedHashMap<>(); m.put("conclusion",kind); m.put("fundamentoConclusion",fundamento);
        m.put("descripcionConclusion",descripcion); m.put("requiereSeguimiento",seguimiento); m.put("requiereDerivacion",derivacion);
        m.put("plazoConclusion",plazo); if ("OTRA".equals(kind)) m.put("denominacionOtra","Conclusión sintética personalizada"); return m;
    }
    private Object seguimiento(LocalDate date, String time) {
        return Map.of("fecha", date.toString(), "hora", time, "tipoVisita", "PROGRAMADA", "fiscalizadorId", "FIS-001",
                "motivo", MARCA + ": verificar implementación de mejora");
    }
    private Object expedienteRequest(String notes) {
        return Map.ofEntries(Map.entry("origen","INICIATIVA_MUNICIPAL"), Map.entry("detalleOrigen",MARCA),
                Map.entry("objetoFiscalizacion","Inspección sintética de condiciones de seguridad"), Map.entry("observacionesIniciales",notes),
                Map.entry("tipoAdministrado","PERSONA_NATURAL"), Map.entry("documentoAdministrado","00000000"),
                Map.entry("nombreAdministrado","Persona Ficticia"), Map.entry("administradoApellidos","Prueba Integrada"),
                Map.entry("telefonoContacto","900000000"), Map.entry("direccionFiscalizada","Calle Sintética 123"),
                Map.entry("referenciaDomicilio","Fixture técnico"), Map.entry("latitud",-12.09), Map.entry("longitud",-77.08));
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, authHeaders(token)), JsonNode.class);
    }
    private ResponseEntity<byte[]> raw(HttpMethod method, String path, String token, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, authHeaders(token)), byte[].class);
    }
    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token); return h;
    }
    private JsonNode body(ResponseEntity<JsonNode> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode(), () -> "HTTP " + response.getStatusCode() + " body=" + response.getBody());
        assertNotNull(response.getBody()); return response.getBody();
    }
    private byte[] binary(ResponseEntity<byte[]> response, String contentType) {
        assertEquals(HttpStatus.OK, response.getStatusCode()); assertEquals(contentType, response.getHeaders().getContentType().toString());
        assertNotNull(response.getBody()); return response.getBody();
    }
    private void assertPdf(byte[] bytes) {
        assertNotNull(bytes); assertTrue(bytes.length > 0); assertTrue(new String(bytes, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
    }
    private void assertStatus(ResponseEntity<JsonNode> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode(), () -> "HTTP " + response.getStatusCode() + " body=" + response.getBody());
    }
    private JsonNode first(JsonNode n) { assertTrue(n.isArray() && !n.isEmpty()); return n.get(0); }
    private boolean contains(JsonNode array, String field, long value) {
        for (JsonNode item : array) if (item.path(field).asLong(Long.MIN_VALUE) == value) return true; return false;
    }
    private boolean contains(JsonNode array, String field, String value) {
        for (JsonNode item : array) if (item.path(field).asText().equals(value)) return true; return false;
    }
}
