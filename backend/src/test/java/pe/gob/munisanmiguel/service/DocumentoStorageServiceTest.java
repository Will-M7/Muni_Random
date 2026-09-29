package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import pe.gob.munisanmiguel.exception.ConflictException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentoStorageServiceTest {
    @Test void aceptaPdfConCabeceraYEOF(@TempDir Path temp) {
        var service = new DocumentoStorageService(temp.toString(), 1024 * 1024);
        var file = new MockMultipartFile("documento", "Solicitud ñ.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes());
        var stored = service.store(file);
        assertTrue(service.load(stored.internalName()).exists());
        service.delete(stored.internalName());
    }

    @Test void rechazaContenidoFalsoAunqueLaExtensionSeaPdf(@TempDir Path temp) {
        var service = new DocumentoStorageService(temp.toString(), 1024 * 1024);
        var file = new MockMultipartFile("documento", "renombrado.pdf", "application/pdf", "texto plano".getBytes());
        assertThrows(ConflictException.class, () -> service.store(file));
    }

    @Test void rechazaPdfIncompleto(@TempDir Path temp) {
        var service = new DocumentoStorageService(temp.toString(), 1024 * 1024);
        var file = new MockMultipartFile("documento", "incompleto.pdf", "application/pdf", "%PDF-1.4".getBytes());
        assertThrows(ConflictException.class, () -> service.store(file));
    }
}
