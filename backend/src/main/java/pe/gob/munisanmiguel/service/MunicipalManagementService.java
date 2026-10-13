package pe.gob.munisanmiguel.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.gob.munisanmiguel.repository.*;
import pe.gob.munisanmiguel.exception.ConflictException;
import java.time.*;
import java.util.*;

@Service
public class MunicipalManagementService {
    public record Assignment(String id,String nombre,String municipioUsername,String municipioNombre){}
    private final FiscalizadorRepository inspectors;private final AppUserRepository users;private final AdminSettingsService settings;private final JdbcTemplate jdbc;
    public MunicipalManagementService(FiscalizadorRepository inspectors,AppUserRepository users,AdminSettingsService settings,JdbcTemplate jdbc){this.inspectors=inspectors;this.users=users;this.settings=settings;this.jdbc=jdbc;}
    @Transactional(readOnly=true) public List<Assignment> list(String actor){settings.requireAdmin(actor);return inspectors.findAll().stream().map(f->new Assignment(f.getId(),f.getNombre(),f.getMunicipioResponsable()==null?null:f.getMunicipioResponsable().getUsername(),f.getMunicipioResponsable()==null?null:f.getMunicipioResponsable().getDisplayName())).sorted(Comparator.comparing(Assignment::nombre)).toList();}
    @Transactional public Assignment assign(String id,String username,String actor){settings.requireAdmin(actor);var f=inspectors.findForIdentityLink(id).orElseThrow(()->new ConflictException("Fiscalizador no encontrado."));var owner=username==null||username.isBlank()?null:users.findByUsernameIgnoreCaseAndActivoTrue(username).orElseThrow(()->new ConflictException("Responsable municipal activo no encontrado."));
        if(owner!=null&&(owner.getArchivedAt()!=null||owner.getRoles().stream().noneMatch(r->"MUNICIPIO".equals(r.getName()))))throw new ConflictException("El responsable debe ser una cuenta municipal activa sin archivar.");
        Long previous=f.getMunicipioResponsable()==null?null:f.getMunicipioResponsable().getId(),next=owner==null?null:owner.getId();
        if(!Objects.equals(previous,next)){var admin=users.findByUsernameIgnoreCaseAndActivoTrue(actor).orElseThrow();jdbc.update("INSERT INTO fiscalizador_gestion_historial(fiscalizador_id,municipio_anterior_id,municipio_nuevo_id,actor_id,fecha) VALUES(?,?,?,?,?)",id,previous,next,admin.getId(),LocalDateTime.now(ZoneId.of("America/Lima")));f.setMunicipioResponsable(owner);inspectors.saveAndFlush(f);}
        return new Assignment(f.getId(),f.getNombre(),owner==null?null:owner.getUsername(),owner==null?null:owner.getDisplayName());
    }
}
