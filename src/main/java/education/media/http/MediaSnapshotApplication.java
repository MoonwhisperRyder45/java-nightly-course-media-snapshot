package education.media.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import education.media.config.SnapshotConfig;
import education.media.infrai.InfraiException;
import education.media.infrai.InfraiStorageClient;
import education.media.snapshot.NightlySnapshotService;
import education.media.snapshot.NightlySnapshotService.Asset;
import education.media.snapshot.NightlySnapshotService.CreatorDelivery;
import education.media.snapshot.NightlySnapshotService.ProcessingJob;
import education.media.snapshot.NightlySnapshotService.SnapshotBatch;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaSnapshotApplication {
    private static final Pattern STRING = Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private MediaSnapshotApplication() {}

    public static void main(String[] args) throws IOException {
        SnapshotConfig config = SnapshotConfig.fromEnvironment(System.getenv());
        NightlySnapshotService service = new NightlySnapshotService(
                new InfraiStorageClient(config.infraiBaseUri(), config.apiKey()), config.bucket(), config.clock());
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/snapshots/nightly", exchange -> handle(exchange, service));
        server.start();
        System.out.println("Media snapshot service listening on http://localhost:" + config.port());
    }

    private static void handle(HttpExchange exchange, NightlySnapshotService service) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String batchId = required(body, "batchId");
            SnapshotBatch batch = new SnapshotBatch(batchId,
                    List.of(new Asset(required(body, "assetId"), required(body, "courseId"), required(body, "sourceKey"))),
                    List.of(new ProcessingJob(required(body, "jobId"), required(body, "assetId"), required(body, "jobState"))),
                    List.of(new CreatorDelivery(required(body, "creatorId"), required(body, "courseId"), required(body, "deliveryState"))));
            var result = service.snapshot(batch);
            respond(exchange, 200, "{\"batchId\":\"" + result.batchId() + "\",\"objectKey\":\""
                    + result.objectKey() + "\",\"state\":\"" + result.state() + "\",\"assetCount\":"
                    + result.assetCount() + ",\"completedJobCount\":" + result.completedJobCount()
                    + ",\"readyDeliveryCount\":" + result.readyDeliveryCount() + "}");
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "{\"error\":\"invalid_snapshot\",\"message\":\"" + exception.getMessage() + "\"}");
        } catch (InfraiException exception) {
            int status = exception.statusCode() >= 400 && exception.statusCode() < 500 ? exception.statusCode() : 502;
            respond(exchange, status, "{\"error\":\"storage_request_rejected\",\"message\":\""
                    + exception.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private static String required(String body, String field) {
        Matcher matcher = Pattern.compile(STRING.pattern().formatted(Pattern.quote(field))).matcher(body);
        if (!matcher.find()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return matcher.group(1);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
