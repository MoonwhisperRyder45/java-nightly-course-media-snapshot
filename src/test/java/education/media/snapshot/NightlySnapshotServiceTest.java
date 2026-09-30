package education.media.snapshot;

import education.media.infrai.InfraiStorage;
import education.media.snapshot.NightlySnapshotService.Asset;
import education.media.snapshot.NightlySnapshotService.CreatorDelivery;
import education.media.snapshot.NightlySnapshotService.ProcessingJob;
import education.media.snapshot.NightlySnapshotService.SnapshotBatch;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class NightlySnapshotServiceTest {
    public static void main(String[] args) {
        FakeStorage storage = new FakeStorage();
        Clock clock = Clock.fixed(Instant.parse("2026-09-29T01:00:00Z"), ZoneOffset.UTC);
        NightlySnapshotService service = new NightlySnapshotService(storage, "course-media-snapshots", clock);
        SnapshotBatch batch = new SnapshotBatch("academy-42",
                List.of(new Asset("lesson-video", "java-101", "ingest/java-101.mp4")),
                List.of(new ProcessingJob("encode-7", "lesson-video", "completed")),
                List.of(new CreatorDelivery("teacher-9", "java-101", "ready")));

        var first = service.snapshot(batch);
        check(first.state().equals("stored"), "first submission should store the manifest");
        check(first.objectKey().equals("nightly/2026-09-29/academy-42.json"), "object key should be deterministic");
        check(storage.putCount == 1, "first submission should write once");

        storage.exists = true;
        var repeated = service.snapshot(batch);
        check(repeated.state().equals("already_stored"), "repeated submission should expose the decision");
        check(storage.putCount == 1, "repeated submission should not write again");
        System.out.println("PASS: repeated batch reuses nightly/2026-09-29/academy-42.json");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeStorage implements InfraiStorage {
        boolean exists;
        int putCount;

        @Override public void createBucket(String name, String idempotencyKey) {}
        @Override public boolean objectExists(String bucket, String key) { return exists; }
        @Override public void putObject(String bucket, String key, byte[] content, String idempotencyKey) { putCount++; }
    }
}
