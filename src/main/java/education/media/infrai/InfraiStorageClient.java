package education.media.infrai;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiStorageClient implements InfraiStorage {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern FOUND = Pattern.compile("\\\"found\\\"\\s*:\\s*(true|false)");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\\\"(?:hint|message)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final URI baseUri;
    private final String apiKey;
    private final HttpClient http;

    public InfraiStorageClient(URI baseUri, String apiKey) {
        this(baseUri, apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiStorageClient(URI baseUri, String apiKey, HttpClient http) {
        this.baseUri = baseUri;
        this.apiKey = apiKey;
        this.http = http;
    }

    @Override
    public void createBucket(String name, String idempotencyKey) {
        // Capability: infrai.storage.bucket.create
        call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + json(name) + "\"}", idempotencyKey);
    }

    @Override
    public boolean objectExists(String bucket, String key) {
        // Capability: infrai.storage.object.head
        String envelope = call("GET", "/v1/storage/object/head/" + segment(bucket) + "/" + path(key), null, null);
        Matcher found = FOUND.matcher(envelope);
        if (!found.find()) {
            throw new IllegalStateException("Object head response did not contain found");
        }
        return Boolean.parseBoolean(found.group(1));
    }

    @Override
    public void putObject(String bucket, String key, byte[] content, String idempotencyKey) {
        // Capability: infrai.storage.object.put
        String body = "{\"data_base64\":\"" + Base64.getEncoder().encodeToString(content) + "\"}";
        call("PUT", "/v1/storage/object/put/" + segment(bucket) + "/" + path(key), body, idempotencyKey);
    }

    private String call(String method, String apiPath, String body, String idempotencyKey) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(apiPath))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .method(method, body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body));
            if (body != null) {
                builder.header("Content-Type", "application/json");
            }
            if (idempotencyKey != null) {
                builder.header("Idempotency-Key", idempotencyKey);
            }

            HttpResponse<String> response;
            try {
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException exception) {
                throw new IllegalStateException("Could not reach object storage", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Object storage request was interrupted", exception);
            }

            String envelope = response.body();
            Matcher ok = OK.matcher(envelope);
            if (!ok.find()) {
                throw new IllegalStateException("Object storage returned an invalid envelope");
            }
            if (Boolean.parseBoolean(ok.group(1))) {
                return envelope;
            }
            if (response.statusCode() == 429 && attempt < 3) {
                sleep(retryDelayMillis(response, attempt));
                continue;
            }
            String code = capture(ERROR_CODE, envelope, "REQUEST_REJECTED");
            String message = capture(ERROR_MESSAGE, envelope, "Object storage rejected the request");
            throw new InfraiException(code, message, response.statusCode());
        }
        throw new IllegalStateException("Retry loop ended unexpectedly");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Duration.ofSeconds(Long.parseLong(value)).toMillis();
                    } catch (NumberFormatException ignored) {
                        return 250L * (1L << attempt);
                    }
                })
                .orElse(250L * (1L << attempt));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry wait was interrupted", exception);
        }
    }

    private static String capture(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String path(String value) {
        return java.util.Arrays.stream(value.split("/", -1))
                .map(InfraiStorageClient::segment)
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
