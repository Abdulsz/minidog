import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/** Dependency-free load generator for MiniDog. Requires Java 21. */
public final class LoadGenerator {

    private static final String EVENT = """
            {"name":"loadtest.request.duration","value":42.5,"type":"histogram",
             "tags":{"service":"loadtest","env":"benchmark"}}""";

    private LoadGenerator() {
    }

    public static void main(String[] args) throws Exception {
        URI endpoint = URI.create(argument(args, 0, "http://localhost:8080/ingest"));
        int concurrency = Integer.parseInt(argument(args, 1, "32"));
        int durationSeconds = Integer.parseInt(argument(args, 2, "15"));
        int batchSize = Integer.parseInt(argument(args, 3, "50"));
        String payload = batch(batchSize);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        AtomicBoolean running = new AtomicBoolean(true);
        LongAdder acceptedRequests = new LongAdder();
        LongAdder rejectedRequests = new LongAdder();
        LongAdder failedRequests = new LongAdder();
        ConcurrentLinkedQueue<Long> latencyMicros = new ConcurrentLinkedQueue<>();

        var executor = Executors.newFixedThreadPool(concurrency);
        long started = System.nanoTime();
        for (int worker = 0; worker < concurrency; worker++) {
            executor.submit(() -> {
                while (running.get()) {
                    HttpRequest request = HttpRequest.newBuilder(endpoint)
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(10))
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();
                    long requestStarted = System.nanoTime();
                    try {
                        int status = client.send(request, HttpResponse.BodyHandlers.discarding())
                                .statusCode();
                        latencyMicros.add((System.nanoTime() - requestStarted) / 1_000);
                        if (status == 202) {
                            acceptedRequests.increment();
                        } else if (status == 429) {
                            rejectedRequests.increment();
                        } else {
                            failedRequests.increment();
                        }
                    } catch (Exception exception) {
                        failedRequests.increment();
                    }
                }
            });
        }

        Thread.sleep(Duration.ofSeconds(durationSeconds));
        running.set(false);
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);
        double elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0;

        long accepted = acceptedRequests.sum();
        long rejected = rejectedRequests.sum();
        long failed = failedRequests.sum();
        List<Long> sortedLatencies = new ArrayList<>(latencyMicros);
        Collections.sort(sortedLatencies);

        System.out.printf(Locale.ROOT, """
                MiniDog load-test result
                endpoint: %s
                concurrency: %d
                duration_seconds: %.2f
                batch_size: %d
                accepted_requests: %d
                rejected_requests_429: %d
                failed_requests: %d
                accepted_events: %d
                accepted_events_per_second: %.2f
                request_latency_ms_p50: %.2f
                request_latency_ms_p95: %.2f
                request_latency_ms_p99: %.2f
                """,
                endpoint, concurrency, elapsedSeconds, batchSize,
                accepted, rejected, failed, accepted * batchSize,
                accepted * batchSize / elapsedSeconds,
                percentile(sortedLatencies, 0.50) / 1_000.0,
                percentile(sortedLatencies, 0.95) / 1_000.0,
                percentile(sortedLatencies, 0.99) / 1_000.0);
    }

    private static String argument(String[] args, int index, String fallback) {
        return args.length > index ? args[index] : fallback;
    }

    private static String batch(int size) {
        return "{\"events\":[" + String.join(",", Collections.nCopies(size, EVENT)) + "]}";
    }

    private static long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(index);
    }
}
