package nihaoya.nasdemo.files;

public record FileItem(
        String path,
        String name,
        boolean isDir,
        long sizeBytes,
        long modifiedAtMs
) {
}

