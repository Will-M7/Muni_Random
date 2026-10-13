package pe.gob.munisanmiguel.service;

import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.AppUserRepository;

@Service
public class MunicipalAccessService {
    private final AppUserRepository users;private final JdbcTemplate jdbc;
    public MunicipalAccessService(AppUserRepository users,JdbcTemplate jdbc){this.users=users;this.jdbc=jdbc;}
    public AppUser current(){var auth=SecurityContextHolder.getContext().getAuthentication();return auth==null?null:users.findByUsernameIgnoreCaseAndActivoTrue(auth.getName()).orElse(null);}
    public boolean municipal(AppUser actor){return actor!=null&&actor.getRoles().stream().anyMatch(r->"MUNICIPIO".equals(r.getName()));}
    public boolean manages(AppUser actor,Fiscalizador inspector){return !municipal(actor)||(inspector!=null&&inspector.getMunicipioResponsable()!=null&&actor.getId().equals(inspector.getMunicipioResponsable().getId()));}
    public void requireAssignment(AppUser actor,Fiscalizador inspector){if(!manages(actor,inspector))throw new AccessDeniedException("El fiscalizador no está asignado a este responsable municipal. ADMIN_SISTEMA debe asignarlo explícitamente.");}
    public boolean canRead(ExpedienteFiscalizacion e,AppUser actor){if(!municipal(actor))return true;if(e.getCreadoPor()!=null&&actor.getId().equals(e.getCreadoPor().getId()))return true;return e.getProgramaciones().stream().anyMatch(p->manages(actor,p.getFiscalizador()));}
    public boolean canReadCurrent(ExpedienteFiscalizacion e){return canRead(e,current());}
    public boolean agendaVisible(ProgramacionFiscalizacion p,AppUser actor){return !municipal(actor)||(p.getExpediente().getCreadoPor()!=null&&actor.getId().equals(p.getExpediente().getCreadoPor().getId()))||manages(actor,p.getFiscalizador());}
    public void requirePathAccess(String path,AppUser actor){
        if(!municipal(actor))return;
        String[] parts=path.split("/");if(parts.length<4)return;String code=null;
        if(parts[2].equals("expedientes")&& !parts[3].equals("nuevo") && !parts[3].equals("fiscalizaciones"))code=parts[3];
        else if(parts[2].equals("actas")&&parts[3].equals("expediente")&&parts.length>4)code=parts[4];
        else if(parts[2].equals("programaciones")&&parts[3].matches("\\d+"))code=caseCode("SELECT e.codigo FROM programacion_fiscalizacion p JOIN expediente_fiscalizacion e ON e.id=p.expediente_id WHERE p.id=?",parts[3]);
        else if(parts[2].equals("actas")&&parts[3].matches("\\d+"))code=caseCode("SELECT e.codigo FROM acta_fiscalizacion a JOIN diligencia_fiscalizacion d ON d.id=a.diligencia_id JOIN expediente_fiscalizacion e ON e.id=d.expediente_id WHERE a.id=?",parts[3]);
        else if(parts[2].equals("revisiones")){
            if(parts[3].matches("\\d+"))code=caseCode("SELECT e.codigo FROM revision_fiscalizacion r JOIN expediente_fiscalizacion e ON e.id=r.expediente_id WHERE r.id=?",parts[3]);
            else if(parts[3].equals("expediente")&&parts.length>4)code=parts[4];
            else if(parts[3].equals("diligencias")&&parts.length>4&&parts[4].matches("\\d+"))code=caseCode("SELECT e.codigo FROM diligencia_fiscalizacion d JOIN expediente_fiscalizacion e ON e.id=d.expediente_id WHERE d.id=?",parts[4]);
        }
        if(code!=null){Boolean allowed=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM expediente_fiscalizacion e WHERE e.codigo=? AND (e.creado_por_id=? OR EXISTS(SELECT 1 FROM programacion_fiscalizacion p JOIN fiscalizador f ON f.id=p.fiscalizador_id WHERE p.expediente_id=e.id AND f.municipio_responsable_id=?)))",Boolean.class,code,actor.getId(),actor.getId());if(!Boolean.TRUE.equals(allowed))throw new AccessDeniedException("El expediente no pertenece al ámbito autorizado de esta cuenta municipal.");}
    }
    private String caseCode(String sql,String id){return jdbc.query(sql,(rs,row)->rs.getString(1),Long.parseLong(id)).stream().findFirst().orElse(null);}
}
