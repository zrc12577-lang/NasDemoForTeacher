package nihaoya.nasdemo.bootstrap;

import nihaoya.nasdemo.settings.SettingsService;
import nihaoya.nasdemo.users.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class Bootstrap implements ApplicationRunner {
    private final UserService userService;
    private final SettingsService settingsService;

    public Bootstrap(UserService userService, SettingsService settingsService) {
        this.userService = userService;
        this.settingsService = settingsService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        userService.ensureRootUser();
        Path baseDir = settingsService.getBaseDir();
        Files.createDirectories(baseDir);
    }
}

