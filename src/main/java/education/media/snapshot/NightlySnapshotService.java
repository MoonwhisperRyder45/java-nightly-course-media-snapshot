package education.media.snapshot;

import education.media.infrai.InfraiStorage;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

public final class NightlySnapshotService {
    public record Asset(String assetId, String courseId, String sourceKey) {}
    public record ProcessingJob(String jobId, String assetId, String state) {}
    public record CreatorDelivery(String creatorId, String courseId, String state) {}
    public record SnapshotBatch(String batchId, List<Asset> assets, List<ProcessingJob> jobs,
                                List<CreatorDelivery> deliveries) {}
    public record SnapshotResult(String batchId, String objectKey, String state, int assetCount,
                                 int completedJobCount, int readyDeliveryCount) {}

    private final InfraiStorage storage;
    private final String bucket;
    private final Clock clock;

    public NightlySnapshotService(InfraiStorage storage, String bucket, Clock clock) {
        this.storage = storage;
        this.bucket = bucket;
        this.clock = clock;
    }

    public SnapshotResult snapshot(SnapshotBatch batch) {
        validate(batch);
        String date = LocalDate.now(clock).toString();
        String objectKey = "nightly/" + date + "/" + batch.batchId() + ".json";
        storage.createBucket(bucket, "bucket-" + bucket);

        int completedJobs = (int) batch.jobs().stream().filter(job -> job.state().equals("completed")).count();
        int readyDeliveries = (int) batch.deliveries().stream().filter(delivery -> delivery.state().equals("ready")).count();
        SnapshotResult result = new SnapshotResult(batch.batchId(), objectKey, "stored", batch.assets().size(),
                completedJobs, readyDeliveries);
        if (storage.objectExists(bucket, objectKey)) {
            return new SnapshotResult(batch.batchId(), objectKey, "already_stored", batch.assets().size(),
                    completedJobs, readyDeliveries);
        }

        String manifest = manifest(batch, date, Instant.now(clock).toString());
        storage.putObject(bucket, objectKey, manifest.getBytes(StandardCharsets.UTF_8), "snapshot-" + batch.batchId());
        return result;
    }

    private static void validate(SnapshotBatch batch) {
        if (batch.batchId() == null || !batch.batchId().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("batchId must contain letters, digits, underscores, or hyphens");
        }
        if (batch.assets() == null || batch.jobs() == null || batch.deliveries() == null) {
            throw new IllegalArgumentException("assets, jobs, and deliveries are required");
        }
    }

    private static String manifest(SnapshotBatch batch, String date, String createdAt) {
        String assets = batch.assets().stream().sorted(Comparator.comparing(Asset::assetId))
                .map(asset -> "{\"assetId\":\"" + esc(asset.assetId()) + "\",\"courseId\":\""
                        + esc(asset.courseId()) + "\",\"sourceKey\":\"" + esc(asset.sourceKey()) + "\"}")
                .reduce((left, right) -> left + "," + right).orElse("");
        String jobs = batch.jobs().stream().sorted(Comparator.comparing(ProcessingJob::jobId))
                .map(job -> "{\"jobId\":\"" + esc(job.jobId()) + "\",\"assetId\":\""
                        + esc(job.assetId()) + "\",\"state\":\"" + esc(job.state()) + "\"}")
                .reduce((left, right) -> left + "," + right).orElse("");
        String deliveries = batch.deliveries().stream().sorted(Comparator.comparing(CreatorDelivery::creatorId))
                .map(delivery -> "{\"creatorId\":\"" + esc(delivery.creatorId()) + "\",\"courseId\":\""
                        + esc(delivery.courseId()) + "\",\"state\":\"" + esc(delivery.state()) + "\"}")
                .reduce((left, right) -> left + "," + right).orElse("");
        return "{\"batchId\":\"" + esc(batch.batchId()) + "\",\"snapshotDate\":\"" + date
                + "\",\"createdAt\":\"" + createdAt + "\",\"assets\":[" + assets
                + "],\"processingJobs\":[" + jobs + "],\"creatorDeliveries\":[" + deliveries + "]}";
    }

    private static String esc(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
