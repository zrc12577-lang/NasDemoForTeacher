package nihaoya.nasdemo.files;

import nihaoya.nasdemo.settings.SettingsService;
import nihaoya.nasdemo.users.UserRecord;
import nihaoya.nasdemo.users.UserService;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {
    private final SettingsService settingsService;
    private final UserService userService;

    public FileService(SettingsService settingsService, UserService userService) {
        this.settingsService = settingsService;
        this.userService = userService;
    }

    public Path userRoot(String username) {
        return settingsService.getBaseDir().resolve("users").resolve(username).normalize();
    }

    public List<FileItem> list(String username, String relDir) throws IOException {
        Path root = userRoot(username);
        Path dir = PathGuard.safeResolve(root, relDir);
        Files.createDirectories(dir);

        List<FileItem> items = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                try {
                    boolean isDir = Files.isDirectory(p);
                    long size = isDir ? 0 : Files.size(p);
                    items.add(new FileItem(
                            root.relativize(p).toString().replace('\\', '/'),
                            p.getFileName().toString(),
                            isDir,
                            size,
                            Files.getLastModifiedTime(p).toMillis()
                    ));
                } catch (IOException ignored) {
                }
            });
        }

        items.sort(Comparator.<FileItem, Boolean>comparing(FileItem::isDir).reversed()
                .thenComparing(FileItem::name, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    public UsageInfo usage(String username) throws IOException {
        UserRecord u = userService.findByUsername(username).orElseThrow();
        long used = DiskUsage.sizeBytes(userRoot(username));
        return new UsageInfo(used, u.quotaBytes());
    }

    public long totalUsedBytes() throws IOException {
        Path base = settingsService.getBaseDir();
        return DiskUsage.sizeBytes(base);
    }

    public void mkdir(String username, String relDir) throws IOException {
        Path root = userRoot(username);
        Path dir = PathGuard.safeResolve(root, relDir);
        Files.createDirectories(dir);
    }

    public void delete(String username, String relPath) throws IOException {
        Path root = userRoot(username);
        Path target = PathGuard.safeResolve(root, relPath);
        if (!Files.exists(target)) return;

        if (Files.isDirectory(target)) {
            try (var walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        } else {
            Files.deleteIfExists(target);
        }
    }

    public void rename(String username, String fromRel, String toRel) throws IOException {
        Path root = userRoot(username);
        Path from = PathGuard.safeResolve(root, fromRel);
        Path to = PathGuard.safeResolve(root, toRel);
        Files.createDirectories(to.getParent());
        Files.move(from, to);
    }

    public Path resolveForDownload(String username, String relPath) {
        Path root = userRoot(username);
        return PathGuard.safeResolve(root, relPath);
    }

    /**
     * Stream a ZIP of {@code relFolderPath} (must be a directory under the user's root). Entry paths use / and UTF-8 names.
     */
    public void zipFolderToOutputStream(String username, String relFolderPath, OutputStream rawOut) throws IOException {
        Path root = userRoot(username);
        Path folder = PathGuard.safeResolve(root, relFolderPath);
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Not a folder");
        }
        Path base = folder.normalize();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOut, 65536), StandardCharsets.UTF_8);
             var stream = Files.walk(base)) {
            var it = stream.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                if (Files.isDirectory(p)) {
                    continue;
                }
                Path rel = base.relativize(p);
                String entryName = rel.toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
    }

    /**
     * Upload one file at a path relative to the user's root (users/&lt;name&gt;/).
     * {@code relPathUnderUserRoot} may contain subdirs (e.g. from folder upload); must not escape the user root.
     */
    public void uploadFileAtUserRelativePath(String username, String relPathUnderUserRoot, long contentLength, InputStream in)
            throws IOException {
        UserRecord u = userService.findByUsername(username).orElseThrow();

        long usedUser = DiskUsage.sizeBytes(userRoot(username));
        long usedTotal = totalUsedBytes();

        long userQuota = u.quotaBytes();
        long totalQuota = settingsService.getTotalQuotaBytes();

        if (contentLength > 0) {
            if (userQuota != Long.MAX_VALUE && usedUser + contentLength > userQuota) {
                throw new IllegalStateException("User quota exceeded");
            }
            if (usedTotal + contentLength > totalQuota) {
                throw new IllegalStateException("Total quota exceeded");
            }
        }

        String normalizedRel = normalizeRelativePath(relPathUnderUserRoot);
        if (normalizedRel.isEmpty()) {
            throw new IllegalArgumentException("Invalid path");
        }

        Path root = userRoot(username);
        Path target = PathGuard.safeResolve(root, normalizedRel);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Join Root-configured upload base + current browse dir + optional path inside multipart filename. */
    public static String joinUploadRelativePath(String defaultUploadRel, String browseDir, String fileRelativePath) {
        String a = safeRelSegment(defaultUploadRel);
        String b = safeRelSegment(browseDir);
        String c = normalizeRelativePath(fileRelativePath);
        String joined = "";
        for (String part : List.of(a, b, c)) {
            if (part.isEmpty()) {
                continue;
            }
            joined = joined.isEmpty() ? part : joined + "/" + part;
        }
        return joined;
    }

    private static String safeRelSegment(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return normalizeRelativePath(s.replace('\\', '/').replaceAll("^/+", ""));
    }

    private static String normalizeRelativePath(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String p = s.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+", "/");
        StringBuilder out = new StringBuilder();
        for (String seg : p.split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            if (".".equals(seg) || "..".equals(seg)) {
                throw new IllegalArgumentException("Invalid path");
            }
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(seg);
        }
        return out.toString();
    }
}

