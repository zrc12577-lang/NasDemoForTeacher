package nihaoya.nasdemo.users;

import nihaoya.nasdemo.settings.SettingsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final JdbcTemplate jdbc;
    private final SettingsService settingsService;
    private final PasswordEncoder encoder;

    public UserService(JdbcTemplate jdbc, SettingsService settingsService, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.settingsService = settingsService;
        this.encoder = encoder;
    }

    private static final RowMapper<UserRecord> USER_ROW = (rs, rowNum) -> new UserRecord(
            rs.getString("username"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            rs.getLong("quota_bytes")
    );

    /** Default root password for first-time setup; keep in sync with login hint in index.html */
    public static final String DEFAULT_ROOT_PASSWORD = "0123/";

    public void ensureRootUser() {
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE username = 'root')",
                Boolean.class
        ));
        if (!exists) {
            jdbc.update(
                    "INSERT INTO users(username, password_hash, role, quota_bytes) VALUES(?, ?, ?, ?)",
                    "root",
                    encoder.encode(DEFAULT_ROOT_PASSWORD),
                    Role.ADMIN.name(),
                    Long.MAX_VALUE
            );
            return;
        }

        // One-time migrations to current DEFAULT_ROOT_PASSWORD
        findByUsername("root").ifPresent(u -> {
            String h = u.passwordHash();
            boolean legacy0123 = encoder.matches("0123", h);
            boolean legacyRoot = encoder.matches("root", h);
            if (legacy0123 || legacyRoot) {
                jdbc.update(
                        "UPDATE users SET password_hash = ? WHERE username = 'root'",
                        encoder.encode(DEFAULT_ROOT_PASSWORD)
                );
            }
        });
    }

    public Optional<UserRecord> findByUsername(String username) {
        List<UserRecord> rows = jdbc.query("SELECT * FROM users WHERE username = ?", USER_ROW, username);
        return rows.stream().findFirst();
    }

    public List<UserRecord> listUsers() {
        return jdbc.query("SELECT * FROM users ORDER BY created_at ASC", USER_ROW);
    }

    public void createUser(String username, String rawPassword, Long quotaBytes) {
        long quota = quotaBytes != null ? quotaBytes : settingsService.getDefaultUserQuotaBytes();
        jdbc.update(
                "INSERT INTO users(username, password_hash, role, quota_bytes) VALUES(?, ?, ?, ?)",
                username,
                encoder.encode(rawPassword),
                Role.USER.name(),
                quota
        );
    }

    public void deleteUser(String username) {
        jdbc.update("DELETE FROM users WHERE username = ? AND username <> 'root'", username);
    }

    public void setUserQuota(String username, long quotaBytes) {
        jdbc.update("UPDATE users SET quota_bytes = ? WHERE username = ? AND username <> 'root'", quotaBytes, username);
    }

    public void setPassword(String username, String rawPassword) {
        jdbc.update("UPDATE users SET password_hash = ? WHERE username = ?", encoder.encode(rawPassword), username);
    }
}

