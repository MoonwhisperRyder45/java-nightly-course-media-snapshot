package education.media.infrai;

public interface InfraiStorage {
    void createBucket(String name, String idempotencyKey);

    boolean objectExists(String bucket, String key);

    void putObject(String bucket, String key, byte[] content, String idempotencyKey);
}
