package pe.gob.munisanmiguel.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AdminSettingsService {
    public record Preferences(boolean mapaInterno, boolean googleExterno, boolean googleUrl, boolean descargarJornadaHtml) {}
    public record DailyLimit(int limite) {}
    private final AppUserRepository users;
    private final UserPreferenceRepository preferences;
    private final GlobalParameterRepository parameters;
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    public AdminSettingsService(AppUserRepository users,UserPreferenceRepository preferences,GlobalParameterRepository parameters){this.users=users;this.preferences=preferences;this.parameters=parameters;}
    public boolean isAdmin(String username){return username!=null&&users.findByUsernameIgnoreCaseAndActivoTrue(username)
            .map(u->u.getRoles().stream().anyMatch(r->"ADMIN_SISTEMA".equals(r.getName()))).orElse(false);}
    public void requireAdmin(String username){if(!isAdmin(username))throw new AccessDeniedException("Solo ADMIN_SISTEMA puede gestionar la configuración.");}
    @Transactional(readOnly=true) public Preferences forUser(String username){var user=users.findByUsernameIgnoreCaseAndActivoTrue(username).orElseThrow();return preferences.findById(user.getId()).map(this::response).orElse(new Preferences(true,true,true,true));}
    @Transactional(readOnly=true) public Preferences forUsername(String username,String actor){requireAdmin(actor);var user=users.findByUsernameIgnoreCase(username).orElseThrow();return preferences.findById(user.getId()).map(this::response).orElse(new Preferences(true,true,true,true));}
    @Transactional public Preferences update(String username,Preferences value,String actor){requireAdmin(actor);var user=users.findByUsernameIgnoreCase(username).orElseThrow();
        if(user.getRoles().stream().anyMatch(r->"MUNICIPIO".equals(r.getName()))&&!value.mapaInterno()&&!value.googleExterno()&&!value.googleUrl())throw new IllegalArgumentException("Mantén al menos un método de ubicación habilitado.");
        var p=preferences.findById(user.getId()).orElseGet(()->{var created=new UserPreference();created.setUserId(user.getId());return created;});
        p.setMapaInterno(value.mapaInterno());p.setGoogleExterno(value.googleExterno());p.setGoogleUrl(value.googleUrl());p.setDescargarJornadaHtml(value.descargarJornadaHtml());p.setActualizadoPor(actor);p.setActualizadoEn(LocalDateTime.now(LIMA));return response(preferences.save(p));}
    @Transactional public void createDefaults(AppUser user){var p=new UserPreference();p.setUserId(user.getId());preferences.save(p);}
    @Transactional(readOnly=true) public int dailyLimit(){return parameters.findById("limite_visitas_diarias").map(GlobalParameter::getIntValue).orElse(5);}
    @Transactional public DailyLimit updateLimit(int limit,String actor){requireAdmin(actor);if(limit<1||limit>100)throw new IllegalArgumentException("El límite debe estar entre 1 y 100.");
        var p=parameters.findById("limite_visitas_diarias").orElseGet(()->{var created=new GlobalParameter();created.setName("limite_visitas_diarias");return created;});
        p.setIntValue(limit);p.setActualizadoPor(actor);p.setActualizadoEn(LocalDateTime.now(LIMA));parameters.save(p);return new DailyLimit(limit);}
    private Preferences response(UserPreference p){return new Preferences(p.isMapaInterno(),p.isGoogleExterno(),p.isGoogleUrl(),p.isDescargarJornadaHtml());}
}
