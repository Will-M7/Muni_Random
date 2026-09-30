package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ActaPdfServiceTest {
    @Test void generaPdfPaginadoConIdentificacionYConstanciaDeFirma(){
        var facts="Hecho objetivamente constatado. ".repeat(1000);
        byte[] bytes=new ActaPdfService().generar(Map.of("actaCodigo","ACT-2026-00001","expedienteCodigo","FIS-2026-00001",
                "fiscalizadorResponsable","Fiscalizador de prueba","hechosConstatados",facts,"firmaFiscalizadoEstado","FIRMA_PENDIENTE"));
        String pdf=new String(bytes,StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF-1.4"));assertTrue(pdf.endsWith("%%EOF\n"));
        var pages=java.util.regex.Pattern.compile("/Count ([1-9][0-9]+)").matcher(pdf);
        assertTrue(pages.find());assertTrue(pdf.contains("Firma del fiscalizador: pendiente de firma manual"));
    }
}
