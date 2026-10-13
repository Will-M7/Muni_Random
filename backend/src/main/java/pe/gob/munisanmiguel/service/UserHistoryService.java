package pe.gob.munisanmiguel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.gob.munisanmiguel.entity.AppUser;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserHistoryService {
    private final JdbcTemplate jdbc;

    public UserHistoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<String> referencesToUser(AppUser user) {
        List<String> result = references("app_user", "id", user.getId(), true);
        result.addAll(usernameAuditReferences(user.getUsername()));
        if (user.getFiscalizador() != null) {
            result.addAll(references("fiscalizador", "id", user.getFiscalizador().getId(), false));
        }
        return result;
    }

    private List<String> usernameAuditReferences(String username) {
        var columns = jdbc.query("SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME IN " +
                        "('actualizado_por','creado_por','actor_username','modificado_por','archived_by') " +
                        "AND DATA_TYPE IN ('char','varchar','tinytext','text') ORDER BY TABLE_NAME, COLUMN_NAME",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2)});
        List<String> found = new ArrayList<>();
        for (String[] ref : columns) {
            String table = "`" + ref[0].replace("`", "``") + "`";
            String column = "`" + ref[1].replace("`", "``") + "`";
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE LOWER(" + column + ") = LOWER(?)", Integer.class, username);
            if (count != null && count > 0) found.add(ref[0] + "." + ref[1] + " (" + count + ")");
        }
        return found;
    }

    private List<String> references(String target, String targetColumn, Object id, boolean excludeTechnical) {
        var columns = jdbc.query("SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_SCHEMA = DATABASE() " +
                        "AND REFERENCED_TABLE_NAME = ? AND REFERENCED_COLUMN_NAME = ? ORDER BY TABLE_NAME, COLUMN_NAME",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, target, targetColumn);
        List<String> found = new ArrayList<>();
        for (String[] ref : columns) {
            // The account's own identity link is not an operation or historical assignment.
            if (target.equals("fiscalizador") && ref[0].equals("app_user")) continue;
            if (excludeTechnical && (ref[0].equals("user_role") || ref[0].equals("user_preference"))) continue;
            String table = "`" + ref[0].replace("`", "``") + "`";
            String column = "`" + ref[1].replace("`", "``") + "`";
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
            if (count != null && count > 0) found.add(ref[0] + "." + ref[1] + " (" + count + ")");
        }
        return found;
    }
}
