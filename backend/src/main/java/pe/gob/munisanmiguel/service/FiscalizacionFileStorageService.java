package pe.gob.munisanmiguel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.exception.ConflictException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
public class FiscalizacionFileStorageService {
    public static final long MAX_FILE_BYTES=10L*1024*1024;
    private final Path root;

    public FiscalizacionFileStorageService(@Value("${app.storage.diligencias-path:uploads/diligencias}") String path) {
        root=Paths.get(path).toAbsolutePath().normalize();
    }

    public StoredFile storeEvidence(MultipartFile file, boolean photoOnly) {
        if(file==null||file.isEmpty()) throw new ConflictException("Selecciona un archivo para adjuntarlo.");
        if(file.getSize()>MAX_FILE_BYTES) throw new ConflictException("Cada archivo puede pesar hasta 10 MB.");
        String name=file.getOriginalFilename()==null?"evidencia":Path.of(file.getOriginalFilename()).getFileName().toString();
        String ext=extension(name);
        String mime=switch(ext){case "jpg","jpeg"->"image/jpeg";case "png"->"image/png";case "webp"->"image/webp";case "pdf"->"application/pdf";default->null;};
        if(mime==null||(photoOnly&&!mime.startsWith("image/")))
            throw new ConflictException(photoOnly?"La fotografía debe ser JPEG, PNG o WEBP.":"Solo se admiten fotografías JPEG, PNG, WEBP o documentos PDF.");
        if(file.getContentType()!=null&&!file.getContentType().isBlank()&&!file.getContentType().equalsIgnoreCase(mime))
            throw new ConflictException("El tipo de archivo no coincide con su contenido.");
        try {
            byte[] head=new byte[12]; int count;
            try(var in=file.getInputStream()){count=in.read(head);}
            if(!validHeader(ext,head,count)) throw new ConflictException("El contenido del archivo no corresponde al formato declarado.");
            if("pdf".equals(ext)&&!hasPdfEndMarker(file))throw new ConflictException("El PDF está incompleto o no tiene un cierre válido.");
            Files.createDirectories(root);
            String internal=UUID.randomUUID()+"."+ext;
            Path target=root.resolve(internal).normalize();
            if(!target.getParent().equals(root)) throw new ConflictException("Ruta de archivo inválida.");
            try(var in=file.getInputStream()){Files.copy(in,target);}
            return new StoredFile(name,internal,mime,file.getSize());
        }catch(IOException ex){throw new ConflictException("No se pudo almacenar el archivo.");}
    }

    public StoredFile storePdf(MultipartFile file) {
        StoredFile stored=storeEvidence(file,false);
        if(!"application/pdf".equals(stored.mimeType())){
            delete(stored.internalName());
            throw new ConflictException("La copia firmada debe ser un archivo PDF.");
        }
        return stored;
    }

    public StoredFile storeGeneratedPdf(byte[] bytes,String filename) {
        if(bytes==null||bytes.length<8||bytes.length>20*1024*1024||bytes[0]!='%'||bytes[1]!='P'||bytes[2]!='D'||bytes[3]!='F'||bytes[4]!='-')
            throw new ConflictException("No se pudo generar el PDF del acta.");
        try {
            Files.createDirectories(root);String internal=UUID.randomUUID()+".pdf";Path target=root.resolve(internal).normalize();
            if(!target.getParent().equals(root))throw new ConflictException("Ruta de archivo inválida.");
            Files.write(target,bytes);return new StoredFile(filename,internal,"application/pdf",bytes.length);
        }catch(IOException ex){throw new ConflictException("No se pudo guardar el PDF del acta.");}
    }

    public Resource load(String internalName) {
        if(internalName==null||!internalName.matches("[a-f0-9-]{36}\\.(?:jpg|jpeg|png|webp|pdf)"))return null;
        Path file=root.resolve(internalName).normalize();return file.getParent().equals(root)&&Files.isRegularFile(file)?new FileSystemResource(file):null;
    }
    public void delete(String internalName){if(internalName==null||!internalName.matches("[a-f0-9-]{36}\\.(?:jpg|jpeg|png|webp|pdf)"))return;try{Files.deleteIfExists(root.resolve(internalName).normalize());}catch(IOException ignored){}}

    private boolean validHeader(String ext,byte[] b,int n){
        return switch(ext){
            case "jpg","jpeg"->n>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff;
            case "png"->n>=8&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'&&(b[4]&255)==0x0d&&(b[5]&255)==0x0a&&(b[6]&255)==0x1a&&(b[7]&255)==0x0a;
            case "webp"->n>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P';
            case "pdf"->n>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-';
            default->false;
        };
    }
    private boolean hasPdfEndMarker(MultipartFile file)throws IOException{
        byte[] bytes=file.getBytes();int start=Math.max(0,bytes.length-2048);
        return new String(bytes,start,bytes.length-start,java.nio.charset.StandardCharsets.ISO_8859_1).contains("%%EOF");
    }
    private String extension(String filename){int dot=filename.lastIndexOf('.');return dot<0?"":filename.substring(dot+1).toLowerCase(Locale.ROOT);}
    public record StoredFile(String originalName,String internalName,String mimeType,long size){}
}
