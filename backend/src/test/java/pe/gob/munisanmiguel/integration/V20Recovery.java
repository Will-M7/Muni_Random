package pe.gob.munisanmiguel.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import pe.gob.munisanmiguel.migration.IdentityMigrationPreflight;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Explicit maintenance tool for isolated rehearsals only. Never changes account links. */
public final class V20Recovery {
    private V20Recovery() {}
    public static Flyway flyway(String url,String user,String password,String target) {
        IsolatedDatabaseInitializer.requireIsolatedUrl(url);
        return Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration")
                .target(target).baselineOnMigrate(false).cleanDisabled(true).load();
    }
    public static void recover(String url,String user,String password) throws Exception {
        IsolatedDatabaseInitializer.requireIsolatedUrl(url);
        var f=flyway(url,user,password,"20");
        var infos=f.info().all();
        long failed=Arrays.stream(infos).filter(i->i.getState().isFailed()).count();
        boolean failedV20=failed==1 && Arrays.stream(infos).anyMatch(i->i.getState().isFailed()&&"20".equals(i.getVersion().getVersion()));
        boolean interruptedAfterRepair=failed==0 && Arrays.stream(infos).anyMatch(i->
                i.getState()==org.flywaydb.core.api.MigrationState.PENDING && "20".equals(i.getVersion().getVersion()));
        if(!failedV20 && !interruptedAfterRepair)
            throw new FlywayException("Se requiere V20 fallida o V20 pendiente con su esquema ya reconciliado.");
        if(Arrays.stream(infos).filter(i->i.getState().isApplied()&&!i.getState().isFailed()).count()!=19)
            throw new FlywayException("Se requieren exactamente V1–V19 aplicadas.");
        for(var i:infos) if(i.getState().isApplied() || i.getState().isFailed()) {
            if(i.getVersion()==null || i.getVersion().compareTo(org.flywaydb.core.api.MigrationVersion.fromVersion("20"))>0
                    || !i.isChecksumMatching() || !i.isDescriptionMatching() || !i.isTypeMatching())
                throw new FlywayException("El historial no coincide con las migraciones originales: "+i.getVersion());
        }
        try(var c=DriverManager.getConnection(url,user,password)) {
            IdentityMigrationPreflight.requireCompatibleIdentities(c);
            if(!columns(c,"expediente_fiscalizacion").containsAll(List.of("dependencia_procedencia","referencia_solicitud")))
                throw new FlywayException("Estado parcial no reconocido: faltan columnas de V20. Auditar antes de continuar.");
            requireScalar(c,"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='expediente_fiscalizacion' AND COLUMN_NAME IN ('dependencia_procedencia','referencia_solicitud') AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=160 AND IS_NULLABLE='YES'",2);
            requireIndex(c,"expediente_fiscalizacion","uk_expediente_solicitud_externa",List.of("dependencia_procedencia","referencia_solicitud"));
            if(interruptedAfterRepair && (!hasIndex(c,"app_user","uk_app_user_fiscalizador") || !hasTable(c,"expediente_documento")))
                throw new FlywayException("Sin V20 fallida solo se permite reanudar un esquema V20 completo.");
            if(hasTable(c,"expediente_documento")) requireDocumentTable(c);
            if(hasIndex(c,"app_user","uk_app_user_fiscalizador")) requireIndex(c,"app_user","uk_app_user_fiscalizador",List.of("fiscalizador_id"));
            var before=snapshot(c);
            var appliedBefore=history(c);
            String migration;
            try(var input=Objects.requireNonNull(V20Recovery.class.getResourceAsStream("/db/migration/V20__fiscalizacion_origen_y_documentos.sql"))) {
                migration=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            }
            var statements=migration.split(";");
            if(!hasIndex(c,"app_user","uk_app_user_fiscalizador")) execute(c,statements[1]);
            requireIndex(c,"app_user","uk_app_user_fiscalizador",List.of("fiscalizador_id"));
            if(!hasTable(c,"expediente_documento")) execute(c,statements[2]);
            // Prove the complete V20 schema before touching history. Re-runs can resume after DDL.
            requireDocumentTable(c);
            if(!unchanged(before,snapshot(c))) throw new FlywayException("Los datos existentes cambiaron durante la reconciliación; detenerse.");
            if(failedV20) f.repair(); // Preverified checksums/descriptions/types; no realignment allowed.
            if(!appliedBefore.equals(history(c))) throw new FlywayException("El historial aplicado cambió durante repair.");
            Flyway.configure().configuration(f.getConfiguration()).skipExecutingMigrations(true).target("20").load().migrate();
            f.validate();
            if(!unchanged(before,snapshot(c))) throw new FlywayException("Los datos existentes cambiaron durante la reparación.");
        }
    }
    private static List<String> columns(Connection c,String table) throws SQLException {
        List<String> result=new ArrayList<>();
        try(var s=c.prepareStatement("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")) {
            s.setString(1,table);try(var rs=s.executeQuery()){while(rs.next())result.add(rs.getString(1));}
        }return result;
    }
    private static boolean hasTable(Connection c,String table)throws SQLException {
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            s.setString(1,table);try(var rs=s.executeQuery()){rs.next();return rs.getInt(1)==1;}
        }
    }
    private static boolean hasIndex(Connection c,String table,String index)throws SQLException {
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?")) {
            s.setString(1,table);s.setString(2,index);try(var rs=s.executeQuery()){rs.next();return rs.getInt(1)>0;}
        }
    }
    private static void requireIndex(Connection c,String table,String index,List<String> expected)throws SQLException {
        List<String> actual=new ArrayList<>();
        try(var s=c.prepareStatement("SELECT COLUMN_NAME,NON_UNIQUE,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=? ORDER BY SEQ_IN_INDEX")) {
            s.setString(1,table);s.setString(2,index);try(var rs=s.executeQuery()){while(rs.next()){if(rs.getInt(2)!=0 || rs.getObject(3)!=null)throw new FlywayException("Índice no único: "+index);actual.add(rs.getString(1));}}
        }
        if(!actual.equals(expected))throw new FlywayException("Índice incompatible: "+index);
    }
    private static void requireDocumentTable(Connection c)throws SQLException {
        if(!columns(c,"expediente_documento").equals(List.of("id","expediente_id","nombre_original","nombre_interno","tipo_mime","tamano_bytes","adjuntado_en","adjuntado_por_id")))
            throw new FlywayException("expediente_documento no coincide con V20.");
        requireIndex(c,"expediente_documento","PRIMARY",List.of("id"));
        requireIndex(c,"expediente_documento","nombre_interno",List.of("nombre_interno"));
        requireScalar(c,"SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_SCHEMA=DATABASE() AND TABLE_NAME='expediente_documento' AND ((COLUMN_NAME='expediente_id' AND REFERENCED_TABLE_NAME='expediente_fiscalizacion' AND REFERENCED_COLUMN_NAME='id') OR (COLUMN_NAME='adjuntado_por_id' AND REFERENCED_TABLE_NAME='app_user' AND REFERENCED_COLUMN_NAME='id'))",2);
        requireScalar(c,"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='expediente_documento' AND ENGINE='InnoDB' AND TABLE_COLLATION='utf8mb4_unicode_ci'",1);
        requireScalar(c,"SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='expediente_documento' AND INDEX_NAME='idx_expediente_documento_expediente' AND COLUMN_NAME='expediente_id' AND SEQ_IN_INDEX=1",1);
        requireScalar(c,"SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='expediente_documento' AND DELETE_RULE IN ('RESTRICT','NO ACTION') AND UPDATE_RULE IN ('RESTRICT','NO ACTION')",2);
        requireScalar(c,"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='expediente_documento' AND IS_NULLABLE='NO' AND ((COLUMN_NAME='id' AND DATA_TYPE='bigint' AND EXTRA='auto_increment') OR (COLUMN_NAME IN ('expediente_id','tamano_bytes','adjuntado_por_id') AND DATA_TYPE='bigint') OR (COLUMN_NAME='adjuntado_en' AND DATA_TYPE='datetime') OR (COLUMN_NAME='nombre_original' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=255) OR (COLUMN_NAME IN ('nombre_interno','tipo_mime') AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=120))",8);
    }
    private static void requireScalar(Connection c,String sql,int expected)throws SQLException {
        try(var s=c.createStatement();var rs=s.executeQuery(sql)){rs.next();if(rs.getInt(1)!=expected)throw new FlywayException("Esquema incompatible con V20.");}
    }
    private static boolean unchanged(Map<String,String> before,Map<String,String> after) {
        return before.entrySet().stream().allMatch(e->Objects.equals(e.getValue(),after.get(e.getKey())));
    }
    private static void execute(Connection c,String sql)throws SQLException {try(var s=c.createStatement()){s.execute(sql);}}
    private static List<String> history(Connection c)throws SQLException {
        List<String> result=new ArrayList<>();try(var s=c.createStatement();var rs=s.executeQuery("SELECT installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success FROM flyway_schema_history WHERE success=1 AND CAST(version AS UNSIGNED)<20 ORDER BY installed_rank")) {
            while(rs.next()){StringJoiner row=new StringJoiner("|");for(int i=1;i<=10;i++)row.add(Objects.toString(rs.getObject(i),"NULL"));result.add(row.toString());}
        }return result;
    }
    /** Hashes all existing business rows without printing hashes/passwords/personal data. */
    public static Map<String,String> snapshot(Connection c)throws Exception {
        List<String> tables=new ArrayList<>();try(var s=c.createStatement();var rs=s.executeQuery("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME <> 'flyway_schema_history' ORDER BY TABLE_NAME")){while(rs.next())tables.add(rs.getString(1));}
        Map<String,String> result=new TreeMap<>();
        for(String table:tables){List<String> rows=new ArrayList<>();try(var s=c.createStatement();var rs=s.executeQuery("SELECT * FROM `"+table.replace("`","``")+"`")) {
            while(rs.next()){var md=MessageDigest.getInstance("SHA-256");for(int i=1;i<=rs.getMetaData().getColumnCount();i++){byte[] value=rs.getBytes(i);md.update((byte)(value==null?0:1));if(value!=null){md.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array());md.update(value);}}rows.add(HexFormat.of().formatHex(md.digest()));}
        }Collections.sort(rows);result.put(table,String.join("",rows));}return result;
    }
    public static void main(String[] args)throws Exception {
        String url=System.getenv("RECOVERY_JDBC_URL"),user=System.getenv("RECOVERY_DB_USER"),password=System.getenv("RECOVERY_DB_PASSWORD");
        if(args.length!=1 || !args[0].equals("--reconcile-and-repair-isolated"))throw new IllegalArgumentException("Usar --reconcile-and-repair-isolated después de regularizar identidades en una copia aislada.");
        recover(url,user,password);System.out.println("V20 reconciliada y validada. No se aplicaron versiones posteriores ni se modificaron cuentas.");
    }
}
