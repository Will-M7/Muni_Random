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
import java.util.Set;
import java.util.zip.ZipInputStream;

@Service
public class FiscalizacionFileStorageService {
    public static final long DEFAULT_MAX_FILE_BYTES=10L*1024*1024;
    @Value("${app.storage.max-size-bytes:10485760}") private long maxFileBytes=DEFAULT_MAX_FILE_BYTES;
    private final Path root;

    public FiscalizacionFileStorageService(@Value("${app.storage.diligencias-path:uploads/diligencias}") String path) {
        root=Paths.get(path).toAbsolutePath().normalize();
    }

    public StoredFile storeEvidence(MultipartFile file, boolean photoOnly) {
        if(file==null||file.isEmpty()) throw new ConflictException("Selecciona un archivo para adjuntarlo.");
        if(file.getSize()>maxFileBytes) throw new ConflictException("El archivo supera el tamaño máximo permitido.");
        String name=file.getOriginalFilename()==null?"evidencia":Path.of(file.getOriginalFilename()).getFileName().toString();
        String ext=extension(name);
        String mime=switch(ext){case "jpg","jpeg"->"image/jpeg";case "png"->"image/png";case "webp"->"image/webp";case "pdf"->"application/pdf";case "docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";case "doc"->"application/msword";default->null;};
        if(mime==null||(photoOnly&&!mime.startsWith("image/")))
            throw new ConflictException(photoOnly?"La fotografía debe ser JPEG, PNG o WEBP.":"Solo se admiten fotografías JPEG, PNG, WEBP o documentos PDF, DOCX o DOC.");
        if(file.getContentType()!=null&&!file.getContentType().isBlank()&&!acceptedMime(ext,file.getContentType()))
            throw new ConflictException("El tipo de archivo no coincide con su contenido.");
        try {
            byte[] head=new byte[12]; int count;
            try(var in=file.getInputStream()){count=in.read(head);}
            if(!validHeader(ext,head,count)) throw new ConflictException("El contenido del archivo no corresponde al formato declarado.");
            if("pdf".equals(ext)&&!hasPdfEndMarker(file))throw new ConflictException("El PDF está incompleto o no tiene un cierre válido.");
            if("docx".equals(ext)&&!isValidDocx(file))throw new ConflictException("El DOCX no contiene una estructura Word válida.");
            if("doc".equals(ext)&&!isValidLegacyDoc(file))throw new ConflictException("El DOC no contiene una estructura Word válida.");
            Files.createDirectories(root);
            String internal=UUID.randomUUID()+"."+ext;
            Path target=root.resolve(internal).normalize();
            if(!target.getParent().equals(root)) throw new ConflictException("Ruta de archivo inválida.");
            try(var in=file.getInputStream()){Files.copy(in,target);}
            catch(IOException ex){Files.deleteIfExists(target);throw ex;}
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
        if(internalName==null||!internalName.matches("[a-f0-9-]{36}\\.(?:jpg|jpeg|png|webp|pdf|docx|doc)"))return null;
        Path file=root.resolve(internalName).normalize();return file.getParent().equals(root)&&Files.isRegularFile(file)?new FileSystemResource(file):null;
    }
    public void delete(String internalName){if(internalName==null||!internalName.matches("[a-f0-9-]{36}\\.(?:jpg|jpeg|png|webp|pdf|docx|doc)"))return;try{Files.deleteIfExists(root.resolve(internalName).normalize());}catch(IOException ignored){}}

    private boolean validHeader(String ext,byte[] b,int n){
        return switch(ext){
            case "jpg","jpeg"->n>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff;
            case "png"->n>=8&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'&&(b[4]&255)==0x0d&&(b[5]&255)==0x0a&&(b[6]&255)==0x1a&&(b[7]&255)==0x0a;
            case "webp"->n>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P';
            case "pdf"->n>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-';
            case "docx"->n>=4&&b[0]=='P'&&b[1]=='K'&&b[2]==3&&b[3]==4;
            case "doc"->n>=8&&(b[0]&255)==0xd0&&(b[1]&255)==0xcf&&(b[2]&255)==0x11&&(b[3]&255)==0xe0&&(b[4]&255)==0xa1&&(b[5]&255)==0xb1&&(b[6]&255)==0x1a&&(b[7]&255)==0xe1;
            default->false;
        };
    }
    private boolean hasPdfEndMarker(MultipartFile file)throws IOException{
        byte[] bytes=file.getBytes();int start=Math.max(0,bytes.length-2048);
        return new String(bytes,start,bytes.length-start,java.nio.charset.StandardCharsets.ISO_8859_1).contains("%%EOF");
    }
    private boolean isValidDocx(MultipartFile file)throws IOException{
        boolean contentTypes=false,document=false;int entries=0;
        try(var zip=new ZipInputStream(file.getInputStream())){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){
            if(++entries>5000)throw new ConflictException("El DOCX contiene demasiados elementos.");
            String name=entry.getName();if("[Content_Types].xml".equals(name))contentTypes=true;if("word/document.xml".equals(name))document=true;
        }}catch(java.util.zip.ZipException ex){return false;}
        return contentTypes&&document;
    }
    private boolean isValidLegacyDoc(MultipartFile file)throws IOException{
        byte[] bytes=file.getBytes();
        byte[] marker="WordDocument".getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        outer:for(int i=0;i<=bytes.length-marker.length;i++){
            for(int j=0;j<marker.length;j++)if(bytes[i+j]!=marker[j])continue outer;
            return true;
        }
        return false;
    }
    private boolean acceptedMime(String ext,String actual){
        String value=actual.toLowerCase(Locale.ROOT);
        return switch(ext){
            case "jpg","jpeg"->Set.of("image/jpeg","image/pjpeg").contains(value);
            case "png"->"image/png".equals(value);case "webp"->"image/webp".equals(value);case "pdf"->"application/pdf".equals(value);
            case "docx"->Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/zip","application/octet-stream").contains(value);
            case "doc"->Set.of("application/msword","application/vnd.ms-word","application/octet-stream").contains(value);
            default->false;
        };
    }
    private String extension(String filename){int dot=filename.lastIndexOf('.');return dot<0?"":filename.substring(dot+1).toLowerCase(Locale.ROOT);}
    public record StoredFile(String originalName,String internalName,String mimeType,long size){}
}
