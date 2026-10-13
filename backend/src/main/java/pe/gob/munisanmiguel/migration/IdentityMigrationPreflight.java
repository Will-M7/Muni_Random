package pe.gob.munisanmiguel.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.*;
import org.springframework.stereotype.Component;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Fail before V20's first non-transactional ALTER, with actionable account IDs. */
@Component
public class IdentityMigrationPreflight implements Callback {
    @Override public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE && context.getMigrationInfo() != null
                && "20".equals(context.getMigrationInfo().getVersion().getVersion());
    }
    @Override public boolean canHandleInTransaction(Event event, Context context) { return true; }
    @Override public String getCallbackName() { return "identity-preflight-v20"; }
    @Override public void handle(Event event, Context context) {
        try { requireCompatibleIdentities(context.getConnection()); }
        catch (SQLException e) { throw new FlywayException("No se pudo auditar la identidad operativa antes de V20", e); }
    }
    public static void requireCompatibleIdentities(Connection connection) throws SQLException {
        List<String> conflicts=new ArrayList<>();
        try (var s=connection.createStatement();var rs=s.executeQuery("SELECT fiscalizador_id, GROUP_CONCAT(id ORDER BY id) FROM app_user WHERE fiscalizador_id IS NOT NULL GROUP BY fiscalizador_id HAVING COUNT(*)>1")) {
            while(rs.next()) conflicts.add(rs.getString(1)+" cuentas="+rs.getString(2));
        }
        try (var s=connection.createStatement();var rs=s.executeQuery("SELECT u.id,u.username,u.fiscalizador_id FROM app_user u WHERE (EXISTS (SELECT 1 FROM user_role ur JOIN app_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.name='FISCALIZADOR')) <> (u.fiscalizador_id IS NOT NULL)")) {
            while(rs.next()) conflicts.add("cuenta="+rs.getLong(1)+" ("+rs.getString(2)+") identidad="+rs.getString(3));
        }
        if(!conflicts.isEmpty()) throw new FlywayException("V20 bloqueada antes del DDL: regularizar identidades con decisión explícita; no borrar cuentas ni autoría. "+String.join("; ",conflicts));
    }
}
