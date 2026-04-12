package nihaoya.nasdemo.files;

import java.nio.file.Path;

public final class PathGuard {
    private PathGuard() {}

    public static Path safeResolve(Path base, String relative) {
        String rel = (relative == null || relative.isBlank()) ? "" : relative;
        Path resolved = base.resolve(rel).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw new IllegalArgumentException("Invalid path");
        }
        return resolved;
    }
}

