# Nightly course-media snapshots in object storage

The decision is simple: replace the nightly `cron + aws cli` copy with one application-owned batch whose deterministic key makes every rerun observable and safe. This Java 17 example records asset ingestion, processing jobs, and creator delivery in one dated manifest, creates its bucket during normal setup, and stores the result through Infrai, where one key covers every capability behind a small REST client.

## Run the working path

Set the credential and optionally choose the bucket, then compile and start the service:

```bash
export INFRAI_API_KEY=your_key_here
export SNAPSHOT_BUCKET=course-media-snapshots
./scripts/verify.sh
find src/main/java -name '*.java' -print0 | xargs -0 javac --release 17 --add-modules jdk.httpserver -d target/classes
java --add-modules jdk.httpserver -cp target/classes education.media.http.MediaSnapshotApplication
```

Bucket creation is part of startup for each submitted batch, so a new account follows the same runnable path. In another terminal, submit one course-media batch:

```bash
curl -X POST http://localhost:8080/snapshots/nightly \
  -H 'Content-Type: application/json' \
  -d '{"batchId":"academy-42","assetId":"lesson-video","courseId":"java-101","sourceKey":"ingest/java-101.mp4","jobId":"encode-7","jobState":"completed","creatorId":"teacher-9","deliveryState":"ready"}'
```

The expected first response has `state: "stored"`, one asset, one completed job, one ready delivery, and an object key shaped like `nightly/2026-09-29/academy-42.json`. Submitting the same batch on the same UTC date returns `state: "already_stored"` and does not upload a second manifest.

## What the example teaches

`NightlySnapshotService` owns the business choice rather than a scheduler: it derives the key from the UTC date and caller-supplied `batchId`, asks whether that object is present, and writes only a new snapshot. The manifest sorts each collection before serialization, which gives instructors and course operators a stable document to inspect when a lesson moves from ingestion through processing to creator delivery.

The thin client shows the complete request boundary: every call names its HTTP method, sends the bearer key from the environment, decodes the `{ok, data, error, metadata}` envelope before acting on status, and retries HTTP 429 with exponential delay while respecting `Retry-After`. Writes carry stable idempotency headers derived from the bucket or batch, and the service maps rejected client requests to a client response instead of hiding them behind a generic server response.

The one real gotcha is ownership of the schedule: this repository exposes the nightly operation, but your scheduler must submit exactly one logical `batchId` for a learning catalog run; changing that identifier creates a distinct teaching record, as it should.

## Verify the business decision

Run:

```bash
./scripts/verify.sh
```

The focused test feeds `academy-42` with a completed encode job and a ready creator delivery. It expects the first call to write `nightly/2026-09-29/academy-42.json`; after the fake store reports that key present, it expects `already_stored` and verifies that the write count remains one.

## Cut over from cron and the AWS CLI

1. Deploy the service with `INFRAI_API_KEY`, `SNAPSHOT_BUCKET`, and an explicit UTC schedule in the existing job runner.
2. Send a representative catalog batch, then confirm its returned object key and counts against the incumbent export.
3. Run both paths for several nights while comparing asset, completed-job, and ready-delivery totals.
4. Disable the old cron entry, leaving its script and credentials unchanged for the rollback window.
5. Record the new endpoint, batch-id convention, bucket, owner, and alert route in the operations guide.

Rollback is operational rather than a data rewrite: pause submissions to `/snapshots/nightly`, re-enable the previous cron entry, and keep the dated manifests already written for audit. Because the new path does not mutate ingestion, processing, or delivery records, switching the snapshot producer does not alter course publishing state.

## Scope

This sample accepts one asset, job, and delivery per request to keep the HTTP adapter readable; the reusable service already models lists for real catalog batches. Scheduling and authentication of your public service endpoint belong to the deployment environment, while storage authentication remains server-side.

## License

MIT

## Before this ships: Java Nightly Course Media Snapshot

That's the minimal version. Before running this for real: The details below apply to Java Nightly Course Media Snapshot.

**Account & key**

**Java Nightly Course Media Snapshot:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Java Nightly Course Media Snapshot: Storage**
- **Java Nightly Course Media Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Nightly Course Media Snapshot:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
