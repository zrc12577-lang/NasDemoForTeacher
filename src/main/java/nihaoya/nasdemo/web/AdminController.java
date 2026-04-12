package nihaoya.nasdemo.web;

import nihaoya.nasdemo.settings.SettingsService;
import nihaoya.nasdemo.users.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserService userService;
    private final SettingsService settingsService;

    public AdminController(UserService userService, SettingsService settingsService) {
        this.userService = userService;
        this.settingsService = settingsService;
    }

    @GetMapping("/users")
    public List<UserDto> listUsers() {
        return userService.listUsers().stream()
                .map(u -> new UserDto(u.username(), u.role().name(), u.quotaBytes()))
                .toList();
    }

    public record UserDto(String username, String role, long quotaBytes) {}

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody CreateUserRequest req) {
        userService.createUser(req.username(), req.password(), req.quotaBytes());
        return Map.of("ok", true);
    }

    public record CreateUserRequest(String username, String password, Long quotaBytes) {}

    @PostMapping("/users/batch")
    public Map<String, Object> batchCreateUsers(@RequestBody BatchCreateUsersRequest req) {
        if (req == null || req.users() == null || req.users().isEmpty()) {
            return Map.of("ok", false, "error", "empty");
        }
        int created = 0;
        for (CreateUserRequest u : req.users()) {
            if (u == null || u.username() == null || u.username().isBlank() || u.password() == null || u.password().isBlank()) {
                continue;
            }
            userService.createUser(u.username().trim(), u.password(), u.quotaBytes());
            created++;
        }
        return Map.of("ok", true, "created", created);
    }

    public record BatchCreateUsersRequest(List<CreateUserRequest> users) {}

    @DeleteMapping("/users/{username}")
    public Map<String, Object> deleteUser(@PathVariable String username) {
        userService.deleteUser(username);
        return Map.of("ok", true);
    }

    @PostMapping("/users/quota")
    public Map<String, Object> setUserQuota(@RequestBody SetQuotaRequest req) {
        userService.setUserQuota(req.username(), req.quotaBytes());
        return Map.of("ok", true);
    }

    public record SetQuotaRequest(String username, long quotaBytes) {}

    @PostMapping("/users/password")
    public Map<String, Object> setUserPassword(@RequestBody SetPasswordRequest req) {
        userService.setPassword(req.username(), req.password());
        return Map.of("ok", true);
    }

    public record SetPasswordRequest(String username, String password) {}

    @GetMapping("/settings")
    public SettingsDto settings() {
        return new SettingsDto(
                settingsService.getBaseDir().toString(),
                settingsService.getTotalQuotaBytes(),
                settingsService.getDefaultUserQuotaBytes(),
                settingsService.getDefaultUploadRelPath()
        );
    }

    @PostMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody SettingsDto dto) {
        settingsService.setBaseDir(dto.baseDir());
        settingsService.setTotalQuotaBytes(dto.totalQuotaBytes());
        settingsService.setDefaultUserQuotaBytes(dto.defaultUserQuotaBytes());
        settingsService.setDefaultUploadRelPath(
                dto.defaultUploadRelPath() == null ? "" : dto.defaultUploadRelPath()
        );
        return Map.of("ok", true);
    }

    public record SettingsDto(
            String baseDir,
            long totalQuotaBytes,
            long defaultUserQuotaBytes,
            String defaultUploadRelPath
    ) {}
}

