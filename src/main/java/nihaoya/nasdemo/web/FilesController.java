package nihaoya.nasdemo.web;

import nihaoya.nasdemo.files.FileItem;
import nihaoya.nasdemo.files.FileService;
import nihaoya.nasdemo.settings.SettingsService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/files")
public class FilesController {
    private final FileService fileService;
    private final SettingsService settingsService;

    public FilesController(FileService fileService, SettingsService settingsService) {
        this.fileService = fileService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public List<FileItem> list(Authentication auth, @RequestParam(defaultValue = "") String dir) throws IOException {
        return fileService.list(auth.getName(), dir);
    }

    @PostMapping("/mkdir")
    public Map<String, Object> mkdir(Authentication auth, @RequestParam String dir) throws IOException {
        fileService.mkdir(auth.getName(), dir);
        return Map.of("ok", true);
    }

    @DeleteMapping
    public Map<String, Object> delete(Authentication auth, @RequestParam String path) throws IOException {
        fileService.delete(auth.getName(), path);
        return Map.of("ok", true);
    }

    @PostMapping("/rename")
    public Map<String, Object> rename(Authentication auth, @RequestBody RenameRequest req) throws IOException {
        fileService.rename(auth.getName(), req.from(), req.to());
        return Map.of("ok", true);
    }

    public record RenameRequest(String from, String to) {}

    /**
     * Multipart part name {@code files} (repeat per file). Use the third parameter of {@code FormData.append}
     * so {@code MultipartFile#getOriginalFilename()} carries relative paths for folder uploads (e.g. {@code a/b.txt}).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            Authentication auth,
            @RequestParam(defaultValue = "") String dir,
            @RequestParam("files") List<MultipartFile> files
    ) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files");
        }
        long nonEmpty = files.stream().filter(f -> f != null && !f.isEmpty()).count();
        if (nonEmpty == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files");
        }
        String user = auth.getName();
        String defaultUpload = settingsService.getDefaultUploadRelPath();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String rawName = Objects.requireNonNullElse(file.getOriginalFilename(), "file").trim();
            if (rawName.isEmpty()) {
                rawName = "file";
            }
            String relUnderUser;
            try {
                relUnderUser = FileService.joinUploadRelativePath(defaultUpload, dir, rawName);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
            if (relUnderUser.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid path");
            }
            long size = file.getSize();
            try (var in = file.getInputStream()) {
                try {
                    fileService.uploadFileAtUserRelativePath(user, relUnderUser, size, in);
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
                }
            }
        }
        return Map.of("ok", true);
    }

    @GetMapping("/download")
    public ResponseEntity<FileSystemResource> download(Authentication auth, @RequestParam String path) throws IOException {
        Path p = fileService.resolveForDownload(auth.getName(), path);
        if (!Files.exists(p) || Files.isDirectory(p)) {
            return ResponseEntity.notFound().build();
        }
        var res = new FileSystemResource(p);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + p.getFileName().toString() + "\"")
                .contentLength(Files.size(p))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(res);
    }

    /** Zip a folder under the user's root (same auth as list/delete). Root 与普通用户一致。 */
    @GetMapping("/download-zip")
    public ResponseEntity<StreamingResponseBody> downloadFolderZip(Authentication auth, @RequestParam String path) {
        String user = auth.getName();
        Path resolved = fileService.resolveForDownload(user, path);
        if (!Files.exists(resolved) || !Files.isDirectory(resolved)) {
            return ResponseEntity.notFound().build();
        }
        String folderName = resolved.getFileName().toString();
        if (folderName.isBlank()) {
            folderName = "folder";
        }
        String zipName = folderName + ".zip";
        String encoded = URLEncoder.encode(zipName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = "attachment; filename=\"folder.zip\"; filename*=UTF-8''" + encoded;

        StreamingResponseBody body = outputStream -> {
            try {
                fileService.zipFolderToOutputStream(user, path, outputStream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }
}

