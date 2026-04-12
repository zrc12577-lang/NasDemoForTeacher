package nihaoya.nasdemo.users;

public record UserRecord(
        String username,
        String passwordHash,
        Role role,
        long quotaBytes
) {
}

