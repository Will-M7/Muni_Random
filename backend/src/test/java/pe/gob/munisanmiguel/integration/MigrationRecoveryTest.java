package pe.gob.munisanmiguel.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import pe.gob.munisanmiguel.migration.IdentityMigrationPreflight;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="municipal.integration",matches="true")
class MigrationRecoveryTest {
    private final String base=System.getProperty("municipal.test.jdbc-url");
    private final String user=System.getProperty("municipal.test.db-user","root");
    private final String password=System.getProperty("municipal.test.db-password","isolated_v20_only");
    private String database() throws Exception {
        IsolatedDatabaseInitializer.requireIsolatedUrl(base);
        String name="v20_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(base,user,password);var s=c.createStatement()) {
            assertTrue(c.getMetaData().getDatabaseProductVersion().startsWith("8.4."));
            s.execute("CREATE DATABASE `"+name+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        return base.replaceFirst("/v20_[a-z0-9_]+","/"+name);
    }
    private void execute(String url,String sql)throws Exception {try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute(sql);}}
    private int count(String url,String sql)throws Exception {try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getInt(1);}}
    private Flyway flyway(String url,String version){return V20Recovery.flyway(url,user,password,version);}
    private void duplicate(String url)throws Exception {
        execute(url,"INSERT INTO app_user(username,password_hash,display_name,cargo,role,fiscalizador_id,activo) SELECT 'synthetic_duplicate',password_hash,'Synthetic owner','TEST','FISCALIZADOR','FIS-001',FALSE FROM app_user WHERE username='fiscalizador'");
        execute(url,"INSERT INTO user_role SELECT u.id,r.id FROM app_user u JOIN app_role r ON r.name='FISCALIZADOR' WHERE u.username='synthetic_duplicate'");
    }
    @Test void baseLimpiaAplicaHastaV28SinV27YConValidez()throws Exception {
        String url=database();var f=flyway(url,"28");assertEquals(27,f.migrate().migrationsExecuted);f.validate();
        assertEquals(27,count(url,"SELECT COUNT(*) FROM flyway_schema_history WHERE success=1"));
        assertEquals(0,count(url,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='27'"));
        assertEquals(1,count(url,"SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND INDEX_NAME='uk_app_user_fiscalizador' AND NON_UNIQUE=0"));
    }
    @Test void recuperaV20ParcialSinteticaConservandoDatosYChecksums()throws Exception {
        String url=database();flyway(url,"19").migrate();duplicate(url);
        var originalFailure=assertThrows(FlywayException.class,()->flyway(url,"20").migrate());
        assertTrue(originalFailure.getMessage().contains("Duplicate entry"));
        assertEquals(1,count(url,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='20' AND success=0"));
        assertEquals(2,count(url,"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='expediente_fiscalizacion' AND COLUMN_NAME IN ('dependencia_procedencia','referencia_solicitud')"));
        assertThrows(FlywayException.class,()->V20Recovery.recover(url,user,password));
        assertEquals(1,count(url,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='20' AND success=0"));
        // This account and identity are created by this test, not inferred from real people.
        execute(url,"INSERT INTO fiscalizador VALUES ('T-SYNTHETIC','T-SYNTHETIC','Synthetic owner','','',FALSE)");
        execute(url,"UPDATE app_user SET fiscalizador_id='T-SYNTHETIC' WHERE username='synthetic_duplicate'");
        Map<String,String> before;try(var c=DriverManager.getConnection(url,user,password)){before=V20Recovery.snapshot(c);}
        V20Recovery.recover(url,user,password);
        try(var c=DriverManager.getConnection(url,user,password)){var after=V20Recovery.snapshot(c);before.forEach((table,digest)->assertEquals(digest,after.get(table)));}
        assertEquals(1,count(url,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='20' AND success=1"));
        assertEquals(1487232706,count(url,"SELECT checksum FROM flyway_schema_history WHERE version='20'"));
        execute(url,"INSERT INTO expediente_fiscalizacion(codigo,fecha_creacion,creado_por_id,origen,estado,objeto_fiscalizacion) SELECT 'T-UPGRADE',NOW(),id,'INICIATIVA_MUNICIPAL','ABIERTO','Synthetic preservation' FROM app_user WHERE username='municipio'");
        execute(url,"INSERT INTO expediente_documento(expediente_id,nombre_original,nombre_interno,tipo_mime,tamano_bytes,adjuntado_en,adjuntado_por_id) SELECT e.id,'synthetic.pdf','synthetic-upgrade.pdf','application/pdf',128,'2026-10-01 10:00:00',u.id FROM expediente_fiscalizacion e JOIN app_user u ON u.username='municipio' WHERE e.codigo='T-UPGRADE'");
        String userRows;try(var c=DriverManager.getConnection(url,user,password)){userRows=V20Recovery.snapshot(c).get("expediente_historial");}
        assertEquals(7,flyway(url,"28").migrate().migrationsExecuted);flyway(url,"28").validate();
        assertEquals(1,count(url,"SELECT COUNT(*) FROM app_user WHERE username='synthetic_duplicate' AND activo=FALSE AND fiscalizador_id='T-SYNTHETIC'"));
        assertEquals(1,count(url,"SELECT COUNT(*) FROM expediente_documento d JOIN expediente_fiscalizacion e ON e.id=d.expediente_id JOIN app_user u ON u.id=d.adjuntado_por_id WHERE d.nombre_interno='synthetic-upgrade.pdf' AND d.tamano_bytes=128 AND d.categoria='DOCUMENTO' AND e.codigo='T-UPGRADE' AND u.username='municipio'"));
        try(var c=DriverManager.getConnection(url,user,password)){assertEquals(userRows,V20Recovery.snapshot(c).get("expediente_historial"));}
        assertEquals(0,count(url,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='27'"));
    }
    @Test void preflightBloqueaAntesDeDejarDDLParcial()throws Exception {
        String url=database();flyway(url,"19").migrate();duplicate(url);
        var f=Flyway.configure().configuration(flyway(url,"20").getConfiguration()).callbacks(new IdentityMigrationPreflight()).load();
        assertThrows(FlywayException.class,f::migrate);
        assertEquals(0,count(url,"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='expediente_fiscalizacion' AND COLUMN_NAME='dependencia_procedencia'"));
    }
    @Test void respaldoAmbiguoSeRechazaSinCambiarDatosNiHistorial()throws Exception {
        String url=base.replaceFirst("/v20_[a-z0-9_]+","/v20_backup");
        Map<String,String> before;try(var c=DriverManager.getConnection(url,user,password)){before=V20Recovery.snapshot(c);}
        var ex=assertThrows(FlywayException.class,()->V20Recovery.recover(url,user,password));
        assertTrue(ex.getMessage().contains("FIS-002"));assertTrue(ex.getMessage().contains("Mayta Arotaype"));
        try(var c=DriverManager.getConnection(url,user,password)){assertEquals(before,V20Recovery.snapshot(c));}
        assertEquals(1,count(url,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='20' AND success=0"));
    }
    @Test void reanudaTrasInterrupcionEntreRepairYRegistroSinDuplicarDDL()throws Exception {
        String url=database();flyway(url,"19").migrate();duplicate(url);assertThrows(FlywayException.class,()->flyway(url,"20").migrate());
        execute(url,"INSERT INTO fiscalizador VALUES ('T-SYNTHETIC','T-SYNTHETIC','Synthetic owner','','',FALSE)");
        execute(url,"UPDATE app_user SET fiscalizador_id='T-SYNTHETIC' WHERE username='synthetic_duplicate'");
        execute(url,"ALTER TABLE app_user ADD CONSTRAINT uk_app_user_fiscalizador UNIQUE(fiscalizador_id)");
        try(var in=getClass().getResourceAsStream("/db/migration/V20__fiscalizacion_origen_y_documentos.sql")){execute(url,new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split(";")[2]);}
        flyway(url,"20").repair(); // Deliberately simulate the documented interruption on synthetic data.
        V20Recovery.recover(url,user,password);flyway(url,"20").validate();
        assertEquals(1,count(url,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='20' AND success=1"));
    }
    @Test void nuevaClaveForaneaRechazaHuerfanosSinBorrarLaCuenta()throws Exception {
        String url=database();flyway(url,"26").migrate();
        execute(url,"INSERT INTO app_user(username,password_hash,display_name,cargo,role,fiscalizador_id,activo) VALUES('synthetic_orphan','untouched-hash','Synthetic','TEST','FISCALIZADOR','NO-IDENTITY',FALSE)");
        assertThrows(FlywayException.class,()->flyway(url,"28").migrate());
        assertEquals(1,count(url,"SELECT COUNT(*) FROM app_user WHERE username='synthetic_orphan' AND password_hash='untouched-hash'"));
    }
    @Test void recoveryNoRealineaUnChecksumHistoricoIncompatible()throws Exception {
        String url=database();flyway(url,"19").migrate();duplicate(url);assertThrows(FlywayException.class,()->flyway(url,"20").migrate());
        // Corruption is deliberately simulated only in this test's new database.
        execute(url,"UPDATE flyway_schema_history SET checksum=0 WHERE version='19'");
        assertThrows(FlywayException.class,()->V20Recovery.recover(url,user,password));
        assertEquals(0,count(url,"SELECT checksum FROM flyway_schema_history WHERE version='19'"));
        assertEquals(1,count(url,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='20' AND success=0"));
    }
}
