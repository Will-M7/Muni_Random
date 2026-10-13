package pe.gob.munisanmiguel.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.entity.ExpedienteDocumento;
import pe.gob.munisanmiguel.entity.ExpedienteHistorial;
import pe.gob.munisanmiguel.entity.OrigenFiscalizacion;
import pe.gob.munisanmiguel.entity.EstadoExpediente;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.exception.NotFoundException;
import pe.gob.munisanmiguel.repository.AppUserRepository;
import pe.gob.munisanmiguel.repository.ExpedienteDocumentoRepository;
import pe.gob.munisanmiguel.repository.ExpedienteFiscalizacionRepository;
import pe.gob.munisanmiguel.repository.ExpedienteHistorialRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class AltaFiscalizacionService {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private final ExpedienteFiscalizacionService expedientes;
    private final ExpedienteFiscalizacionRepository expedienteRepository;
    private final ExpedienteDocumentoRepository documentos;
    private final FiscalizacionFileStorageService storage;
    private final AppUserRepository users;
    private final ExpedienteHistorialRepository historial;

    public AltaFiscalizacionService(ExpedienteFiscalizacionService expedientes,ExpedienteFiscalizacionRepository expedienteRepository,
            ExpedienteDocumentoRepository documentos,FiscalizacionFileStorageService storage,AppUserRepository users,ExpedienteHistorialRepository historial){
        this.expedientes=expedientes;this.expedienteRepository=expedienteRepository;this.documentos=documentos;this.storage=storage;this.users=users;this.historial=historial;
    }

    @Transactional
    public ExpedienteResponse crear(CrearFiscalizacionRequest request,List<MultipartFile> files,String username){
        storage.validateDocumentBatch(files);
        if(request.expediente().origen()==OrigenFiscalizacion.SOLICITUD&&
                blank(request.referenciaSolicitud())==null)
            throw new ConflictException("Indique el número o referencia de la solicitud recibida.");
        if(blank(request.dependenciaProcedencia())!=null&&blank(request.referenciaSolicitud())!=null&&
                expedienteRepository.existsByDependenciaProcedenciaIgnoreCaseAndReferenciaSolicitudIgnoreCase(
                        blank(request.dependenciaProcedencia()),blank(request.referenciaSolicitud())))
            throw new ConflictException("Ya existe una fiscalización para esa solicitud y dependencia.");
        var result=expedientes.crear(request.expediente(),username);
        var expediente=expedienteRepository.findByCodigo(result.codigo()).orElseThrow();
        expediente.setDependenciaProcedencia(blank(request.dependenciaProcedencia()));
        expediente.setReferenciaSolicitud(blank(request.referenciaSolicitud()));
        if(request.primeraProgramacion()!=null)expedientes.programar(result.codigo(),request.primeraProgramacion(),username);
        if(files!=null&&!files.isEmpty()){
            var storedNames=new ArrayList<String>();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCompletion(int status){
                    if(status!=STATUS_COMMITTED)storedNames.forEach(storage::delete);
                }
            });
            var user=users.findByUsernameIgnoreCaseAndActivoTrue(username).orElseThrow();
            for(var file:files){
                var stored=storage.storeEvidence(file,false);
                storedNames.add(stored.internalName());
                var document=new ExpedienteDocumento();document.setExpediente(expediente);
                document.setNombreOriginal(stored.originalName());document.setNombreInterno(stored.internalName());
                document.setTipoMime(stored.mimeType());document.setTamanoBytes(stored.size());
                document.setAdjuntadoEn(LocalDateTime.now(LIMA));document.setAdjuntadoPor(user);
                documentos.save(document);
                registrarAdjunto(expediente,document,user);
            }
        }
        return expedientes.obtener(result.codigo());
    }

    @Transactional(readOnly=true)
    public List<ExpedienteDocumentoResponse> listarDocumentos(String codigo){
        expedienteRepository.findByCodigo(codigo).orElseThrow(()->new NotFoundException("Expediente no encontrado."));
        return documentos.findAllByExpedienteCodigoOrderByAdjuntadoEnAscIdAsc(codigo).stream()
                .map(d->new ExpedienteDocumentoResponse(d.getId(),d.getNombreOriginal(),d.getTipoMime(),
                        d.getTamanoBytes(),d.getAdjuntadoEn(),d.getAdjuntadoPor().getUsername(),d.getCategoria())).toList();
    }

    @Transactional
    public List<ExpedienteDocumentoResponse> adjuntar(String codigo,List<MultipartFile> files,String username){
        return adjuntar(codigo,files,username,"DOCUMENTO");
    }

    @Transactional
    public List<ExpedienteDocumentoResponse> adjuntarActas(String codigo,List<MultipartFile> files,String username){
        return adjuntar(codigo,files,username,"ACTA_ADJUNTA");
    }

    private List<ExpedienteDocumentoResponse> adjuntar(String codigo,List<MultipartFile> files,String username,String categoria){
        if(files==null||files.isEmpty())throw new ConflictException("Selecciona al menos un documento.");
        storage.validateDocumentBatch(files);
        if("ACTA_ADJUNTA".equals(categoria)&&files.stream().anyMatch(file->file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")))
            throw new ConflictException("Las actas adjuntas deben ser archivos PDF.");
        var expediente=expedienteRepository.findByCodigo(codigo).orElseThrow(()->new NotFoundException("Expediente no encontrado."));
        if(expediente.getEstado()==EstadoExpediente.CERRADO||expediente.getEstado()==EstadoExpediente.CANCELADO)
            throw new ConflictException("No se pueden adjuntar documentos a un expediente cerrado o cancelado.");
        var user=users.findByUsernameIgnoreCaseAndActivoTrue(username).orElseThrow();
        var storedNames=new ArrayList<String>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storedNames.forEach(storage::delete);}
        });
        for(var file:files){
            var stored=storage.storeEvidence(file,false);storedNames.add(stored.internalName());
            var document=new ExpedienteDocumento();document.setExpediente(expediente);
            document.setNombreOriginal(stored.originalName());document.setNombreInterno(stored.internalName());
            document.setTipoMime(stored.mimeType());document.setTamanoBytes(stored.size());document.setCategoria(categoria);
            document.setAdjuntadoEn(LocalDateTime.now(LIMA));document.setAdjuntadoPor(user);documentos.save(document);
            registrarAdjunto(expediente,document,user);
        }
        return listarDocumentos(codigo);
    }

    private void registrarAdjunto(pe.gob.munisanmiguel.entity.ExpedienteFiscalizacion expediente,ExpedienteDocumento document,pe.gob.munisanmiguel.entity.AppUser user){
        var event=new ExpedienteHistorial();event.setExpediente(expediente);event.setFechaHora(document.getAdjuntadoEn());
        event.setActor(user);event.setActorUsername(user.getUsername());
        event.setTipoEvento("ACTA_ADJUNTA".equals(document.getCategoria())?"ACTA_ADJUNTA_SUBIDA":"DOCUMENTO_ADJUNTADO");
        event.setDescripcion(("ACTA_ADJUNTA".equals(document.getCategoria())?"Acta adjunta subida: ":"Documento adjuntado: ")+document.getNombreOriginal());
        historial.save(event);
    }

    @Transactional(readOnly=true)
    public DocumentoDescarga descargar(String codigo,Long id){
        var document=documentos.findByIdAndExpedienteCodigo(id,codigo)
                .orElseThrow(()->new NotFoundException("Documento no encontrado en este expediente."));
        Resource resource=storage.load(document.getNombreInterno());
        if(resource==null)throw new NotFoundException("El archivo del documento no está disponible.");
        return new DocumentoDescarga(resource,document.getNombreOriginal(),document.getTipoMime());
    }
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    public record DocumentoDescarga(Resource resource,String name,String mime){}
}
