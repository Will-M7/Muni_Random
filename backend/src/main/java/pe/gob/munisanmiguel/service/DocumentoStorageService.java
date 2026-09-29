package pe.gob.munisanmiguel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.exception.ConflictException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DocumentoStorageService {
    private final Path root;
    private final long maxSize;

    public DocumentoStorageService(@Value("${app.storage.path:uploads/solicitudes}") String path,
                                   @Value("${app.storage.max-size-bytes:10485760}") long maxSize) {
        this.root = Paths.get(path).toAbsolutePath().normalize();
        this.maxSize = maxSize;
    }

    public StoredDocument store(MultipartFile file) {
        validar(file);
        String internal = UUID.randomUUID() + ".pdf";
        try {
            Files.createDirectories(root);
            Path target = root.resolve(internal).normalize();
            if (!target.getParent().equals(root)) throw new ConflictException("Ruta de documento inválida.");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
            return new StoredDocument(file.getOriginalFilename(), internal, "application/pdf",
                    file.getSize(), internal, LocalDateTime.now());
        } catch (IOException ex) {
            throw new ConflictException("No se pudo almacenar el documento PDF.");
        }
    }

    public Resource load(String internalName) {
        if (internalName == null || !internalName.matches("[a-f0-9-]{36}\\.pdf")) return null;
        Path file = root.resolve(internalName).normalize();
        if (!file.getParent().equals(root) || !Files.isRegularFile(file)) return null;
        return new FileSystemResource(file);
    }

    public void delete(String internalName) {
        if (internalName == null || !internalName.matches("[a-f0-9-]{36}\\.pdf")) return;
        try { Files.deleteIfExists(root.resolve(internalName).normalize()); } catch (IOException ignored) { }
    }

    private void validar(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ConflictException("Debe adjuntar un archivo PDF.");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".pdf") || file.getSize() > maxSize ||
                (file.getContentType() != null && !file.getContentType().equalsIgnoreCase("application/pdf"))) {
            throw new ConflictException("El documento debe ser PDF y no superar el tamaño máximo permitido.");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] content = input.readAllBytes();
            byte[] header = content.length < 5 ? content : java.util.Arrays.copyOf(content, 5);
            boolean headerOk = header.length == 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
            boolean eofOk = new String(content, java.nio.charset.StandardCharsets.ISO_8859_1).contains("%%EOF");
            if (!headerOk || !eofOk)
                throw new ConflictException("El contenido del archivo no corresponde a un PDF válido.");
        } catch (IOException ex) { throw new ConflictException("No se pudo validar el documento PDF."); }
    }

    public record StoredDocument(String originalName, String internalName, String contentType, long size, String relativePath, LocalDateTime uploadedAt) { }
}
