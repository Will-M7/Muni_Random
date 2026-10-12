package pe.gob.munisanmiguel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDate;

/** Persistent, shared reservation counter for PeruAPI.com Free requests. */
@Service
public class DniDailyQuotaService {
    public static final int LIMIT = 45;
    private final JdbcTemplate jdbc;

    public DniDailyQuotaService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(LocalDate date) {
        jdbc.update("INSERT IGNORE INTO peruapi_dni_daily_quota (quota_date, request_count) VALUES (?, 0)", date);
        return jdbc.update("UPDATE peruapi_dni_daily_quota SET request_count = request_count + 1, updated_at = CURRENT_TIMESTAMP WHERE quota_date = ? AND request_count < ?", date, LIMIT) == 1;
    }

    @Transactional(readOnly = true)
    public int used(LocalDate date) {
        Integer count = jdbc.query("SELECT request_count FROM peruapi_dni_daily_quota WHERE quota_date = ?", rs -> rs.next() ? rs.getInt(1) : 0, date);
        return count == null ? 0 : count;
    }
}
