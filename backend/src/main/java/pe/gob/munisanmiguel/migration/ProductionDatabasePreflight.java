package pe.gob.munisanmiguel.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** V27 may only run as part of the first migration of an empty production database. */
@Component
@Profile("prod")
public class ProductionDatabasePreflight implements Callback {
    @Override public boolean supports(Event event, Context context) { return event == Event.BEFORE_MIGRATE; }
    @Override public boolean canHandleInTransaction(Event event, Context context) { return true; }
    @Override public String getCallbackName() { return "production-empty-database-preflight"; }
    @Override public void handle(Event event, Context context) {
        try { verify(context.getConnection()); }
        catch (SQLException e) { throw new FlywayException("No se pudo verificar la base productiva antes de migrar.", e); }
    }
    static void verify(Connection connection) throws SQLException {
        var tables = new HashSet<String>();
        try (var s = connection.createStatement(); var rs = s.executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()")) {
            while (rs.next()) tables.add(rs.getString(1));
        }
        if (tables.isEmpty()) return;
        // Flyway may have created its empty history table before this callback.
        if (tables.size() == 1 && tables.contains("flyway_schema_history")
                && scalar(connection, "SELECT COUNT(*) FROM flyway_schema_history") == 0) return;
        if (tables.contains("production_seed_guard") && tables.contains("flyway_schema_history")
                && scalar(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE version='27' AND success=1") == 1
                && scalar(connection, "SELECT COUNT(*) FROM production_seed_guard WHERE id=1 AND verified=1") == 1) return;
        throw new FlywayException("Producción requiere una base completamente vacía o una instalación productiva verificada por V27. No se migró ni limpió esta base. Un aprovisionamiento interrumpido requiere revisión; no usar repair ni baseline.");
    }
    private static long scalar(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var rs = s.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
    }
}
