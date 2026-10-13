package pe.gob.munisanmiguel.migration;

import java.sql.*;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.api.FlywayException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionDatabasePreflightTest {
    private Connection database(String... tables) throws Exception {
        var c=mock(Connection.class); var s=mock(Statement.class); var r=mock(ResultSet.class);
        when(c.createStatement()).thenReturn(s); when(s.executeQuery(anyString())).thenReturn(r);
        var index=new java.util.concurrent.atomic.AtomicInteger(-1);
        when(r.next()).thenAnswer(i->index.incrementAndGet()<tables.length);
        when(r.getString(1)).thenAnswer(i->tables[index.get()]);
        return c;
    }
    @Test void acceptsOnlyAnEmptyNewDatabase() throws Exception {
        assertDoesNotThrow(()->ProductionDatabasePreflight.verify(database()));
    }
    @Test void refusesLegacyOrInterruptedDatabaseBeforeV27() throws Exception {
        assertThrows(FlywayException.class,()->ProductionDatabasePreflight.verify(database("app_user","flyway_schema_history")));
    }
    @Test void refusesAMarkerWithoutSuccessfulV27() throws Exception {
        assertThrows(FlywayException.class,()->ProductionDatabasePreflight.verify(database("app_user","flyway_schema_history","production_seed_guard")));
    }
}
