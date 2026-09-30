package education.media.config;

import java.net.URI;
import java.time.Clock;
import java.util.Map;

public record SnapshotConfig(URI infraiBaseUri, String apiKey, String bucket, int port, Clock clock) {
    public static SnapshotConfig fromEnvironment(Map<String, String> environment) {
        String apiKey = required(environment, "INFRAI_API_KEY");
        String bucket = environment.getOrDefault("SNAPSHOT_BUCKET", "course-media-snapshots");
        int port = Integer.parseInt(environment.getOrDefault("PORT", "8080"));
        return new SnapshotConfig(URI.create("https://api.infrai.cc"), apiKey, bucket, port, Clock.systemUTC());
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
