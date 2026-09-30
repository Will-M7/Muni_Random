package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import pe.gob.munisanmiguel.exception.ConflictException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FiscalizacionFileStorageServiceTest {
    @Test void almacenaUnaFotoConFirmaPngYPermiteRecuperarla(@TempDir Path temp){
        var storage=new FiscalizacionFileStorageService(temp.toString());
        byte[] png={(byte)0x89,'P','N','G',13,10,26,10,0,0,0,0};
        var stored=storage.storeEvidence(new MockMultipartFile("archivo","prueba.png","image/png",png),true);
        assertTrue(storage.load(stored.internalName()).exists());
        storage.delete(stored.internalName());
    }

    @Test void rechazaEjecutableRenombradoComoFoto(@TempDir Path temp){
        var storage=new FiscalizacionFileStorageService(temp.toString());
        var executable=new MockMultipartFile("archivo","programa.exe","application/octet-stream","MZfake".getBytes());
        assertThrows(ConflictException.class,()->storage.storeEvidence(executable,true));
    }

    @Test void exigeMarcadorFinalEnPdfSubido(@TempDir Path temp){
        var storage=new FiscalizacionFileStorageService(temp.toString());
        var incomplete=new MockMultipartFile("archivo","acta.pdf","application/pdf","%PDF-1.4\ncontenido sin EOF".getBytes());
        assertThrows(ConflictException.class,()->storage.storePdf(incomplete));
        var valid=new MockMultipartFile("archivo","acta.pdf","application/pdf","%PDF-1.4\n%%EOF\n".getBytes());
        var stored=storage.storePdf(valid);assertNotNull(storage.load(stored.internalName()));storage.delete(stored.internalName());
    }
}
