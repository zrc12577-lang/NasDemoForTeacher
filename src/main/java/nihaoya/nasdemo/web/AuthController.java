package nihaoya.nasdemo.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import nihaoya.nasdemo.security.SingleSessionService;
import nihaoya.nasdemo.users.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SingleSessionService singleSessionService;

    public AuthController(
            UserService userService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SingleSessionService singleSessionService
    ) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.singleSessionService = singleSessionService;
    }

    /** Public list for the login screen (usernames only). */
    @GetMapping("/usernames")
    public List<String> usernames() {
        return userService.listUsers().stream().map(u -> u.username()).toList();
    }

    public record LoginRequest(String username, String password) {}
    public record ChangePasswordRequest(String oldPassword, String newPassword) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (req.username() == null || req.username().isBlank() || req.password() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("ok", false, "error", "empty"));
        }
        try {
            UsernamePasswordAuthenticationToken authReq = UsernamePasswordAuthenticationToken.unauthenticated(
                    req.username().trim(),
                    req.password()
            );
            Authentication authentication = authenticationManager.authenticate(authReq);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            HttpSession session = request.getSession(true);
            singleSessionService.onLogin(authentication.getName(), session);
            securityContextRepository.saveContext(context, request, response);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "error", "invalid_credentials"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (auth != null && auth.isAuthenticated()) {
            singleSessionService.onLogout(auth.getName(), session);
        }
        SecurityContextHolder.clearContext();
        securityContextRepository.saveContext(null, request, response);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestBody ChangePasswordRequest req,
            Authentication auth
    ) {
        if (req == null || req.oldPassword() == null || req.newPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("ok", false, "error", "empty"));
        }
        String oldPassword = req.oldPassword().trim();
        String newPassword = req.newPassword().trim();
        if (oldPassword.isEmpty() || newPassword.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("ok", false, "error", "empty"));
        }
        if (newPassword.length() < 4) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("ok", false, "error", "password_too_short"));
        }

        String username = auth.getName();
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, oldPassword)
            );
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "error", "invalid_old_password"));
        }

        userService.setPassword(username, newPassword);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
