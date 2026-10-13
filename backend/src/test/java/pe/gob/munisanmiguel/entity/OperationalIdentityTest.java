package pe.gob.munisanmiguel.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class OperationalIdentityTest {
    private AppUser account(String role) { var u=new AppUser();var r=new AppRole();r.setName(role);u.getRoles().add(r);return u; }
    private Fiscalizador identity() { var f=new Fiscalizador();f.setId("T-OWN");f.setActivo(true);return f; }
    @Test void municipioYAdminNoNecesitanIdentidadOperativa(){assertTrue(account("MUNICIPIO").hasCompatibleIdentity());assertTrue(account("ADMIN_SISTEMA").hasCompatibleIdentity());}
    @Test void fiscalizadorSinIdentidadNoSeAutentica(){assertFalse(account("FISCALIZADOR").hasCompatibleIdentity());}
    @Test void fiscalizadorInactivoOArchivadoNoSeAutentica(){var u=account("FISCALIZADOR");var f=identity();u.setFiscalizador(f);assertTrue(u.hasCompatibleIdentity());f.setActivo(false);assertFalse(u.hasCompatibleIdentity());f.setActivo(true);u.setArchivedAt(LocalDateTime.now());assertFalse(u.hasCompatibleIdentity());}
    @Test void municipioConVinculoIncompatibleNoSeAutentica(){var u=account("MUNICIPIO");u.setFiscalizador(identity());assertFalse(u.hasCompatibleIdentity());}
}
