package com.freshflow.possimulator;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supporting module (spec-01 &sect;1.2) &mdash; generates the scan event stream that demonstrates
 * the system end to end.
 *
 * <p><b>What this module is.</b> A small command-line client over the two REST endpoints of spec-04
 * that the demonstration path starts from: optionally {@code POST /api/batches} on
 * {@code inventory-service} (FR1), to put stock on the shelf, and then {@code POST /api/transactions}
 * on {@code sales-service} (FR5, FR6a, FR6b) at a steady rate. The transactions are what
 * {@code sales-service} turns into the {@code item-sold} stream, which is what FR2 deducts from,
 * FR12 windows and FR13 joins against. Not a bounded context: it has no port, no datastore and no
 * layered package structure, because spec-01 &sect;2.1 applies to services and this is a client of
 * them. No Spring and no Jackson either &mdash; JDK 21 only, so it runs from {@code target/classes}
 * with nothing else on the classpath, and the JSON it sends is written by hand against spec-04's
 * {@code CreateBatchRequest} and {@code CreateTransactionRequest}.
 *
 * <p><b>Usage.</b> After {@code ./mvnw -o package} (which runs the tests; nothing here skips them):
 * <pre>
 *   java -cp pos-simulator/target/classes com.freshflow.possimulator.PosSimulator \
 *       [--sales-url=http://localhost:8082] [--inventory-url=http://localhost:8081] \
 *       [--rate=30] [--duration=120] [--seed=42] \
 *       [--skus=SKU-YOG-1KG:0.7,SKU-STR-250G:0.3] [--till=TILL-01] [--seed-batches=false]
 * </pre>
 * <table border="1">
 *   <tr><th>Option</th><th>Default</th><th>Meaning</th></tr>
 *   <tr><td>{@code --sales-url}</td><td>{@code http://localhost:8082}</td><td>Where {@code sales-service} listens (spec-01 &sect;1.2)</td></tr>
 *   <tr><td>{@code --inventory-url}</td><td>{@code http://localhost:8081}</td><td>Where {@code inventory-service} listens</td></tr>
 *   <tr><td>{@code --rate}</td><td>{@code 30}</td><td>Transactions per minute, at least 1</td></tr>
 *   <tr><td>{@code --duration}</td><td>{@code 120}</td><td>Seconds to run for; {@code 0} sends nothing, which with {@code --seed-batches=true} seeds and stops</td></tr>
 *   <tr><td>{@code --seed}</td><td>{@code 42}</td><td>Seed for the SKU and quantity draws, so a run is repeatable</td></tr>
 *   <tr><td>{@code --skus}</td><td>{@code SKU-YOG-1KG:0.7,SKU-STR-250G:0.3}</td><td>SKUs and their relative weights, see {@link SkuMix}</td></tr>
 *   <tr><td>{@code --till}</td><td>{@code TILL-01}</td><td>{@code tillId} stamped on every transaction</td></tr>
 *   <tr><td>{@code --seed-batches}</td><td>{@code false}</td><td>{@code true} first registers one batch per SKU: 400 units, expiring 70 minutes from now, unit cost 2.10 AUD, {@code supplierRef SIM-n}</td></tr>
 * </table>
 *
 * <p>Each transaction carries one line item of one SKU, drawn from the mix, with a quantity of 1
 * to 3 weighted towards 1, and {@code occurredAt} stamped from the clock at the moment it is sent
 * (ISO-8601, UTC) &mdash; the producer-assigned event time spec-01 &sect;2.6 requires all the way
 * down the pipeline. One line is printed per request with the HTTP status and the identifier the
 * service returned, and a summary at the end.
 *
 * <p><b>Exit codes.</b> {@code 0} when every transaction answered 201; {@code 1} when at least
 * one did not, or the arguments were rejected; {@code 2} when a service could not be reached at
 * all, which is reported with the URL that refused the connection.
 */
public final class PosSimulator {

    public static final int EXIT_OK = 0;
    public static final int EXIT_FAILURES = 1;
    public static final int EXIT_UNREACHABLE = 2;

    static final String USAGE = """
            usage: java -cp pos-simulator/target/classes com.freshflow.possimulator.PosSimulator [options]
              --sales-url=URL        sales-service base URL          (default http://localhost:8082)
              --inventory-url=URL    inventory-service base URL      (default http://localhost:8081)
              --rate=N               transactions per minute, N >= 1 (default 30)
              --duration=SECONDS     how long to send for, >= 0      (default 120)
              --seed=N               random seed                     (default 42)
              --skus=SKU:W,SKU:W     SKUs with relative weights      (default SKU-YOG-1KG:0.7,SKU-STR-250G:0.3)
              --till=ID              tillId on every transaction     (default TILL-01)
              --seed-batches=BOOL    POST one batch per SKU first    (default false)
              --help                 print this and exit""";

    /** Seeded batches: 400 units, expiring 70 minutes out so FR13 has something to find. */
    static final int SEED_BATCH_QUANTITY = 400;
    static final Duration SEED_BATCH_SHELF_LIFE = Duration.ofMinutes(70);
    static final String SEED_BATCH_UNIT_COST = "2.10";
    static final String SEED_BATCH_CURRENCY = "AUD";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int BODY_EXCERPT_LENGTH = 200;

    private final Options options;
    private final HttpClient client;
    private final PrintStream out;
    private final Clock clock;
    private final Random random;

    PosSimulator(Options options, HttpClient client, PrintStream out, Clock clock) {
        this.options = Objects.requireNonNull(options, "options");
        this.client = Objects.requireNonNull(client, "client");
        this.out = Objects.requireNonNull(out, "out");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = new Random(options.seed());
    }

    public static void main(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                System.out.println(USAGE);
                return;
            }
        }
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("pos-simulator: " + e.getMessage());
            System.err.println(USAGE);
            System.exit(EXIT_FAILURES);
            return;
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        System.exit(new PosSimulator(options, client, System.out, Clock.systemUTC()).run());
    }

    /** Runs the demonstration to completion and returns the process exit code. */
    public int run() {
        out.printf("pos-simulator: sales=%s inventory=%s rate=%d/min duration=%ds seed=%d till=%s "
                        + "skus=%s seed-batches=%s%n",
                options.salesUrl(), options.inventoryUrl(), options.ratePerMinute(),
                options.durationSeconds(), options.seed(), options.tillId(), options.skus(),
                options.seedBatches());
        try {
            int batchesSeeded = options.seedBatches() ? seedBatches() : 0;
            return sendTransactions(batchesSeeded);
        } catch (ServiceUnreachable e) {
            out.printf("pos-simulator: cannot connect to %s (%s). Is the service running? "
                    + "Exiting with code %d.%n", e.url(), e.getMessage(), EXIT_UNREACHABLE);
            return EXIT_UNREACHABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.println("pos-simulator: interrupted");
            return EXIT_FAILURES;
        }
    }

    /** FR1 through spec-04's createBatch, one batch per SKU. Returns how many answered 201. */
    private int seedBatches() throws ServiceUnreachable, InterruptedException {
        Instant expiryAt = clock.instant().plus(SEED_BATCH_SHELF_LIFE);
        URI endpoint = options.inventoryUrl().resolve("/api/batches");
        int seeded = 0;
        int n = 1;
        for (String sku : options.skus().skus()) {
            String supplierRef = "SIM-" + n++;
            Response response = post(endpoint, batchJson(sku, SEED_BATCH_QUANTITY, expiryAt,
                    SEED_BATCH_UNIT_COST, SEED_BATCH_CURRENCY, supplierRef));
            if (response.status() == 201) {
                seeded++;
                out.printf("batch  HTTP 201 batchId=%s sku=%s quantity=%d expiryAt=%s supplierRef=%s%n",
                        extract(response.body(), "batchId"), sku, SEED_BATCH_QUANTITY, expiryAt,
                        supplierRef);
            } else {
                out.printf("batch  HTTP %s sku=%s body=%s%n", statusText(response), sku,
                        excerpt(response.body()));
            }
        }
        return seeded;
    }

    /** FR5 through spec-04's createTransaction, at the configured rate. Returns the exit code. */
    private int sendTransactions(int batchesSeeded) throws ServiceUnreachable, InterruptedException {
        int total = options.transactionCount();
        long intervalNanos = options.interval().toNanos();
        URI endpoint = options.salesUrl().resolve("/api/transactions");
        long startedAt = System.nanoTime();
        int created = 0;
        int failed = 0;

        for (int i = 1; i <= total; i++) {
            String sku = options.skus().pick(random);
            int quantity = pickQuantity(random);
            Instant occurredAt = clock.instant();
            Response response = post(endpoint, transactionJson(options.tillId(), occurredAt, sku, quantity));
            if (response.status() == 201) {
                created++;
                out.printf("#%04d  HTTP 201 transactionId=%s sku=%s quantity=%d occurredAt=%s%n",
                        i, extract(response.body(), "transactionId"), sku, quantity, occurredAt);
            } else {
                failed++;
                out.printf("#%04d  HTTP %s sku=%s quantity=%d occurredAt=%s body=%s%n",
                        i, statusText(response), sku, quantity, occurredAt, excerpt(response.body()));
            }
            if (i < total) {
                sleepUntil(startedAt + intervalNanos * i);
            }
        }

        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt);
        if (options.seedBatches()) {
            out.printf("pos-simulator: batches seeded=%d/%d%n", batchesSeeded, options.skus().skus().size());
        }
        out.printf("pos-simulator: sent=%d created=%d failed=%d elapsed=%ds%n",
                total, created, failed, elapsedSeconds);
        return failed == 0 ? EXIT_OK : EXIT_FAILURES;
    }

    private Response post(URI endpoint, String json) throws ServiceUnreachable, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body() == null ? "" : response.body());
        } catch (ConnectException | HttpConnectTimeoutException e) {
            throw new ServiceUnreachable(endpoint, e);
        } catch (IOException e) {
            // Anything else -- a reset mid-request, a response that never came -- is one failed
            // request, counted and reported, not the end of the run.
            return new Response(-1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void sleepUntil(long targetNanos) throws InterruptedException {
        long remaining = targetNanos - System.nanoTime();
        if (remaining > 0) {
            TimeUnit.NANOSECONDS.sleep(remaining);
        }
    }

    /**
     * Units on the line: 1 to 3, weighted towards 1 &mdash; sixty per cent singles, thirty per
     * cent pairs, ten per cent threes &mdash; which is roughly what a basket of fresh goods looks
     * like at a till, and enough variety to make FR2's FEFO span a batch boundary now and then.
     */
    static int pickQuantity(Random random) {
        double draw = random.nextDouble();
        if (draw < 0.6) {
            return 1;
        }
        return draw < 0.9 ? 2 : 3;
    }

    // ---------------------------------------------------------------- JSON by hand

    /** spec-04 {@code CreateTransactionRequest} with a single line item. */
    static String transactionJson(String tillId, Instant occurredAt, String sku, int quantity) {
        return "{\"tillId\":" + jsonString(tillId)
                + ",\"occurredAt\":" + jsonString(occurredAt.toString())
                + ",\"lineItems\":[{\"sku\":" + jsonString(sku)
                + ",\"quantity\":" + quantity + "}]}";
    }

    /** spec-04 {@code CreateBatchRequest}. */
    static String batchJson(String sku, int quantity, Instant expiryAt, String unitCostAmount,
                            String currency, String supplierRef) {
        return "{\"sku\":" + jsonString(sku)
                + ",\"quantity\":" + quantity
                + ",\"expiryAt\":" + jsonString(expiryAt.toString())
                + ",\"unitCost\":{\"amount\":" + unitCostAmount
                + ",\"currency\":" + jsonString(currency) + "}"
                + ",\"supplierRef\":" + jsonString(supplierRef) + "}";
    }

    /** A JSON string literal, with the escapes RFC 8259 requires. */
    static String jsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * The value of a top-level string field in a response body, or {@code ?} when it is not
     * there. Enough to echo an identifier; this module parses no JSON.
     */
    static String extract(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : "?";
    }

    private static String statusText(Response response) {
        return response.status() < 0 ? "ERR" : Integer.toString(response.status());
    }

    private static String excerpt(String body) {
        String oneLine = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= BODY_EXCERPT_LENGTH
                ? oneLine
                : oneLine.substring(0, BODY_EXCERPT_LENGTH) + "...";
    }

    private record Response(int status, String body) {
    }

    /** A service refused or timed out the connection itself; the run cannot continue. */
    private static final class ServiceUnreachable extends Exception {

        private final URI url;

        ServiceUnreachable(URI url, IOException cause) {
            super(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(),
                    cause);
            this.url = url;
        }

        URI url() {
            return url;
        }
    }

    // ---------------------------------------------------------------- command line

    /** The parsed command line. Every option has a default; see the class javadoc. */
    public record Options(URI salesUrl,
                          URI inventoryUrl,
                          int ratePerMinute,
                          int durationSeconds,
                          long seed,
                          SkuMix skus,
                          String tillId,
                          boolean seedBatches) {

        public static final String DEFAULT_SALES_URL = "http://localhost:8082";
        public static final String DEFAULT_INVENTORY_URL = "http://localhost:8081";
        public static final int DEFAULT_RATE_PER_MINUTE = 30;
        public static final int DEFAULT_DURATION_SECONDS = 120;
        public static final long DEFAULT_SEED = 42L;
        public static final String DEFAULT_SKUS = "SKU-YOG-1KG:0.7,SKU-STR-250G:0.3";
        public static final String DEFAULT_TILL_ID = "TILL-01";

        private static final List<String> KEYS = List.of("sales-url", "inventory-url", "rate",
                "duration", "seed", "skus", "till", "seed-batches");

        public static Options defaults() {
            return parse();
        }

        /**
         * Parses {@code --key=value} arguments; anything absent takes its default.
         *
         * @throws IllegalArgumentException naming the offending option when one is unknown,
         *                                  malformed or out of range
         */
        public static Options parse(String... args) {
            String salesUrl = DEFAULT_SALES_URL;
            String inventoryUrl = DEFAULT_INVENTORY_URL;
            String rate = Integer.toString(DEFAULT_RATE_PER_MINUTE);
            String duration = Integer.toString(DEFAULT_DURATION_SECONDS);
            String seed = Long.toString(DEFAULT_SEED);
            String skus = DEFAULT_SKUS;
            String till = DEFAULT_TILL_ID;
            String seedBatches = "false";

            for (String arg : args) {
                if (arg == null || !arg.startsWith("--") || !arg.contains("=")) {
                    throw new IllegalArgumentException(
                            "argument '" + arg + "' is not of the form --key=value");
                }
                int eq = arg.indexOf('=');
                String key = arg.substring(2, eq);
                String value = arg.substring(eq + 1);
                if (!KEYS.contains(key)) {
                    throw new IllegalArgumentException("unknown option --" + key);
                }
                if (value.isBlank()) {
                    throw new IllegalArgumentException("--" + key + " needs a value");
                }
                switch (key) {
                    case "sales-url" -> salesUrl = value;
                    case "inventory-url" -> inventoryUrl = value;
                    case "rate" -> rate = value;
                    case "duration" -> duration = value;
                    case "seed" -> seed = value;
                    case "skus" -> skus = value;
                    case "till" -> till = value;
                    case "seed-batches" -> seedBatches = value;
                    default -> throw new IllegalStateException(key);
                }
            }

            return new Options(
                    baseUrl("sales-url", salesUrl),
                    baseUrl("inventory-url", inventoryUrl),
                    integer("rate", rate, 1),
                    integer("duration", duration, 0),
                    longInteger("seed", seed),
                    SkuMix.parse(skus),
                    till.trim(),
                    bool("seed-batches", seedBatches));
        }

        /** How many transactions a run sends: rate x duration / 60, rounded up. */
        public int transactionCount() {
            return (int) Math.ceil(ratePerMinute * durationSeconds / 60.0);
        }

        /** The gap between two transactions. */
        public Duration interval() {
            return Duration.of(Math.round(60_000_000_000.0 / ratePerMinute), ChronoUnit.NANOS);
        }

        private static URI baseUrl(String key, String value) {
            URI uri;
            try {
                uri = URI.create(value.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("--" + key + " is not a URL: '" + value + "'");
            }
            if (uri.getScheme() == null || uri.getHost() == null
                    || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                throw new IllegalArgumentException(
                        "--" + key + " must be an absolute http(s) URL, got '" + value + "'");
            }
            String text = uri.toString();
            return URI.create(text.endsWith("/") ? text.substring(0, text.length() - 1) : text);
        }

        private static int integer(String key, String value, int minimum) {
            int parsed;
            try {
                parsed = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("--" + key + " must be an integer, got '" + value + "'");
            }
            if (parsed < minimum) {
                throw new IllegalArgumentException(
                        "--" + key + " must be at least " + minimum + ", got " + parsed);
            }
            return parsed;
        }

        private static long longInteger(String key, String value) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("--" + key + " must be an integer, got '" + value + "'");
            }
        }

        private static boolean bool(String key, String value) {
            String normalised = value.trim().toLowerCase();
            if (normalised.equals("true")) {
                return true;
            }
            if (normalised.equals("false")) {
                return false;
            }
            throw new IllegalArgumentException("--" + key + " must be true or false, got '" + value + "'");
        }
    }
}
