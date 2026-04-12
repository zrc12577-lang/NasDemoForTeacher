package nihaoya.nasdemo.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SingleSessionService {
    private final Map<String, String> latestSessionByUser = new ConcurrentHashMap<>();
    private final Map<String, HttpSession> sessionsById = new ConcurrentHashMap<>();

    public synchronized void onLogin(String username, HttpSession newSession) {
        String newSessionId = newSession.getId();
        sessionsById.put(newSessionId, newSession);

        String oldSessionId = latestSessionByUser.put(username, newSessionId);
        if (oldSessionId == null || oldSessionId.equals(newSessionId)) {
            return;
        }

        HttpSession oldSession = sessionsById.remove(oldSessionId);
        if (oldSession != null) {
            try {
                oldSession.invalidate();
            } catch (IllegalStateException ignored) {
                // Session may already be invalidated/expired.
            }
        }
    }

    public synchronized void onLogout(String username, HttpSession session) {
        if (session == null) {
            return;
        }
        String sessionId = session.getId();
        sessionsById.remove(sessionId);
        latestSessionByUser.computeIfPresent(username, (u, currentId) ->
                currentId.equals(sessionId) ? null : currentId
        );
    }
}
