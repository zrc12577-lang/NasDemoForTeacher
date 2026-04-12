package nihaoya.nasdemo.web;

import nihaoya.nasdemo.files.FileService;
import nihaoya.nasdemo.files.UsageInfo;
import nihaoya.nasdemo.settings.SettingsService;
import nihaoya.nasdemo.users.UserRecord;
import nihaoya.nasdemo.users.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class MeController {
    private final UserService userService;
    private final FileService fileService;
    private final SettingsService settingsService;

    public MeController(UserService userService, FileService fileService, SettingsService settingsService) {
        this.userService = userService;
        this.fileService = fileService;
        this.settingsService = settingsService;
    }

    @GetMapping("/me")
    public MeResponse me(Authentication auth) throws IOException {
        String username = auth.getName();
        UserRecord u = userService.findByUsername(username).orElseThrow();
        UsageInfo usage = fileService.usage(username);
        return new MeResponse(
                u.username(),
                u.role().name(),
                usage.usedBytes(),
                usage.quotaBytes(),
                settingsService.getDefaultUploadRelPath()
        );
    }

    public record MeResponse(
            String username,
            String role,
            long usedBytes,
            long quotaBytes,
            String defaultUploadRelPath
    ) {}
}

