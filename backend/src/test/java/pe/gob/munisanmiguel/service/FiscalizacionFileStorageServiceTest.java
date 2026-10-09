package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import pe.gob.munisanmiguel.exception.ConflictException;

import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Test void aceptaDocxEstructuralYDocOlePeroRechazaContenidoFalso(@TempDir Path temp)throws Exception{
        var storage=new FiscalizacionFileStorageService(temp.toString());
        var docx=new MockMultipartFile("archivo","informe.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",docx());
        var storedDocx=storage.storeEvidence(docx,false);assertTrue(storage.load(storedDocx.internalName()).exists());
        byte[] marker="WordDocument".getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        byte[] doc=new byte[8+marker.length];
        byte[] header={(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,(byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1};
        System.arraycopy(header,0,doc,0,header.length);System.arraycopy(marker,0,doc,header.length,marker.length);
        var storedDoc=storage.storeEvidence(new MockMultipartFile("archivo","informe.doc","application/msword",doc),false);
        assertTrue(storage.load(storedDoc.internalName()).exists());
        assertThrows(ConflictException.class,()->storage.storeEvidence(new MockMultipartFile("archivo","renombrado.doc","application/msword",header),false));
        assertThrows(ConflictException.class,()->storage.storeEvidence(new MockMultipartFile("archivo","falso.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document","PK-no-es-zip".getBytes()),false));
        storage.delete(storedDocx.internalName());storage.delete(storedDoc.internalName());
    }

    private byte[] docx()throws Exception{var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("[Content_Types].xml"));zip.write("<Types/>".getBytes());zip.closeEntry();zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write("<document/>".getBytes());zip.closeEntry();}return bytes.toByteArray();}
}
