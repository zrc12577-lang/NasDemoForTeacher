package nihaoya.nasdemo.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class SettingsService {
    private final JdbcTemplate jdbc;

    public SettingsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureDefaults();
    }

    public Path getBaseDir() {
        return Path.of(get("base_dir"));
    }

    public long getTotalQuotaBytes() {
        return Long.parseLong(get("total_quota_bytes"));
    }

    public long getDefaultUserQuotaBytes() {
        return Long.parseLong(get("default_user_quota_bytes"));
    }

    /** Relative path under each user's home (users/&lt;name&gt;/…); uploads land here plus current「目录」. May be blank. */
    public String getDefaultUploadRelPath() {
        return get("default_upload_rel_path");
    }

    public void setBaseDir(String baseDir) {
        put("base_dir", baseDir);
    }

    public void setTotalQuotaBytes(long bytes) {
        put("total_quota_bytes", Long.toString(bytes));
    }

    public void setDefaultUserQuotaBytes(long bytes) {
        put("default_user_quota_bytes", Long.toString(bytes));
    }

    public void setDefaultUploadRelPath(String path) {
        put("default_upload_rel_path", path == null ? "" : path.trim());
    }

    private void ensureDefaults() {
        String userDir = System.getProperty("user.dir");
        String defaultBaseDir = Path.of(userDir, "data", "storage").toString();
        jdbc.update("INSERT OR IGNORE INTO settings(k, v) VALUES(?, ?)", "base_dir", defaultBaseDir);
        jdbc.update("INSERT OR IGNORE INTO settings(k, v) VALUES(?, ?)", "total_quota_bytes", "107374182400");
        jdbc.update("INSERT OR IGNORE INTO settings(k, v) VALUES(?, ?)", "default_user_quota_bytes", "1073741824");
        jdbc.update("INSERT OR IGNORE INTO settings(k, v) VALUES(?, ?)", "default_upload_rel_path", "");
    }

    private String get(String key) {
        return jdbc.queryForObject("SELECT v FROM settings WHERE k = ?", String.class, key);
    }

    private void put(String key, String value) {
        jdbc.update("INSERT INTO settings(k, v) VALUES(?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v", key, value);
    }
}

