package nihaoya.nasdemo.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DiskUsage {
    private DiskUsage() {}

    public static long sizeBytes(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0L;
        try (var stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        }
    }
}

