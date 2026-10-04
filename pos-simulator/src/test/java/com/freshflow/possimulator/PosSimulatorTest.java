package com.freshflow.possimulator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The simulator's command line, its draws, the JSON it writes by hand, and a whole run against a
 * stand-in for the two services.
 *
 * <p>The stand-in is the JDK's own {@code com.sun.net.httpserver}, so the module stays at zero
 * dependencies beyond the test starter it already had. It records every request body and answers
 * with a fixed identifier, which is enough to check that the simulator posts spec-04's shapes to
 * spec-04's paths, paces the configured number of transactions, and reports what came back.
 */
@DisplayName("pos-simulator: command line, draws and the demonstration run")
class PosSimulatorTest {

    private static final Instant TEN_AM = Instant.parse("2026-10-04T10:00:00Z");
    private static final Clock FIXED = Clock.fixed(TEN_AM, ZoneOffset.UTC);

    // ------------------------------------------------------------ argument parsing

    @Test
    @DisplayName("no arguments gives the documented defaults")
    void defaultsWhenNoArguments() {
        PosSimulator.Options options = PosSimulator.Options.parse();

        assertThat(options.salesUrl()).isEqualTo(URI.create("http://localhost:8082"));
        assertThat(options.inventoryUrl()).isEqualTo(URI.create("http://localhost:8081"));
        assertThat(options.ratePerMinute()).isEqualTo(30);
        assertThat(options.durationSeconds()).isEqualTo(120);
        assertThat(options.seed()).isEqualTo(42L);
        assertThat(options.skus().toString()).isEqualTo("SKU-YOG-1KG:0.7,SKU-STR-250G:0.3");
        assertThat(options.tillId()).isEqualTo("TILL-01");
        assertThat(options.seedBatches()).isFalse();
        // 30 a minute for 120 seconds is 60 transactions, one every two seconds.
        assertThat(options.transactionCount()).isEqualTo(60);
        assertThat(options.interval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(PosSimulator.Options.defaults()).isEqualTo(options);
    }

    @Test
    @DisplayName("every option can be overridden as --key=value")
    void everyOptionCanBeOverridden() {
        PosSimulator.Options options = PosSimulator.Options.parse(
                "--sales-url=http://sales.test:9082/",
                "--inventory-url=https://inventory.test",
                "--rate=600",
                "--duration=1",
                "--seed=7",
                "--skus=SKU-A:3,SKU-B:1",
                "--till=TILL-09",
                "--seed-batches=true");

        assertThat(options.salesUrl())
                .as("a trailing slash is dropped so the endpoint paths resolve cleanly")
                .isEqualTo(URI.create("http://sales.test:9082"));
        assertThat(options.inventoryUrl()).isEqualTo(URI.create("https://inventory.test"));
        assertThat(options.ratePerMinute()).isEqualTo(600);
        assertThat(options.durationSeconds()).isEqualTo(1);
        assertThat(options.seed()).isEqualTo(7L);
        assertThat(options.skus().skus()).containsExactly("SKU-A", "SKU-B");
        assertThat(options.tillId()).isEqualTo("TILL-09");
        assertThat(options.seedBatches()).isTrue();
        // 600 a minute for one second: ten transactions, 100 ms apart.
        assertThat(options.transactionCount()).isEqualTo(10);
        assertThat(options.interval()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    @DisplayName("a malformed argument is rejected with a message naming the option")
    void rejectsMalformedArguments() {
        assertThatThrownBy(() -> PosSimulator.Options.parse("--rate=abc"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("--rate");
        assertThatThrownBy(() -> PosSimulator.Options.parse("--rate=0")).hasMessageContaining("--rate");
        assertThatThrownBy(() -> PosSimulator.Options.parse("--duration=-1")).hasMessageContaining("--duration");
        assertThatThrownBy(() -> PosSimulator.Options.parse("--seed=4.2")).hasMessageContaining("--seed");
        assertThatThrownBy(() -> PosSimulator.Options.parse("--seed-batches=yes")).hasMessageContaining("--seed-batches");
        assertThatThrownBy(() -> PosSimulator.Options.parse("--sales-url=localhost:8082")).hasMessageContaining("--sales-url");
        assertThatThrownBy(() -> PosSimulator.Options.parse("--skus=")).hasMessageContaining("--skus");
        assertThatThrownBy(() -> PosSimulator.Options.parse("--colour=red")).hasMessageContaining("--colour");
        assertThatThrownBy(() -> PosSimulator.Options.parse("rate=30")).hasMessageContaining("--key=value");
    }

    // ------------------------------------------------------------------- draws

    @Test
    @DisplayName("quantity is 1 to 3, weighted towards 1, and repeatable from the seed")
    void quantityIsOneToThreeWeightedTowardOne() {
        int[] counts = new int[4];
        Random random = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            counts[PosSimulator.pickQuantity(random)]++;
        }

        assertThat(counts[0]).as("never zero").isZero();
        assertThat(counts[1]).as("singles outnumber pairs").isGreaterThan(counts[2]);
        assertThat(counts[2]).as("pairs outnumber threes").isGreaterThan(counts[3]);
        assertThat(counts[3]).as("but threes do happen").isPositive();

        Random first = new Random(42);
        Random second = new Random(42);
        for (int i = 0; i < 100; i++) {
            assertThat(PosSimulator.pickQuantity(first)).isEqualTo(PosSimulator.pickQuantity(second));
        }
    }

    // ------------------------------------------------------------- JSON by hand

    @Test
    @DisplayName("the transaction body is spec-04's CreateTransactionRequest, with strings escaped")
    void transactionJsonIsTheSpec04RequestShape() {
        assertThat(PosSimulator.transactionJson("TILL-01", TEN_AM, "SKU-YOG-1KG", 2))
                .isEqualTo("{\"tillId\":\"TILL-01\",\"occurredAt\":\"2026-10-04T10:00:00Z\","
                        + "\"lineItems\":[{\"sku\":\"SKU-YOG-1KG\",\"quantity\":2}]}");
        assertThat(PosSimulator.jsonString("a\"b\\c\nd")).isEqualTo("\"a\\\"b\\\\c\\nd\"");
    }

    @Test
    @DisplayName("the batch body is spec-04's CreateBatchRequest")
    void batchJsonIsTheSpec04RequestShape() {
        assertThat(PosSimulator.batchJson("SKU-YOG-1KG", 400, TEN_AM.plus(70, ChronoUnit.MINUTES),
                "2.10", "AUD", "SIM-1"))
                .isEqualTo("{\"sku\":\"SKU-YOG-1KG\",\"quantity\":400,\"expiryAt\":\"2026-10-04T11:10:00Z\","
                        + "\"unitCost\":{\"amount\":2.10,\"currency\":\"AUD\"},\"supplierRef\":\"SIM-1\"}");
    }

    @Test
    @DisplayName("an identifier is read back out of a response body without a JSON library")
    void extractsIdentifiersFromResponses() {
        assertThat(PosSimulator.extract("{\"transactionId\" : \"TXN-1A2B\", \"tillId\":\"TILL-01\"}",
                "transactionId")).isEqualTo("TXN-1A2B");
        assertThat(PosSimulator.extract("{\"error\":\"UNKNOWN_SKU\"}", "transactionId")).isEqualTo("?");
        assertThat(PosSimulator.extract(null, "batchId")).isEqualTo("?");
    }

    // ------------------------------------------------------------------ the run

    @Test
    @DisplayName("a run seeds one batch per SKU, sends rate x duration / 60 transactions and summarises")
    void runSeedsBatchesAndSendsTheConfiguredNumberOfTransactions() throws Exception {
        try (FakeServices services = new FakeServices(201)) {
            PosSimulator.Options options = PosSimulator.Options.parse(
                    "--sales-url=" + services.url(), "--inventory-url=" + services.url(),
                    "--rate=600", "--duration=1", "--seed=42", "--seed-batches=true");
            ByteArrayOutputStream captured = new ByteArrayOutputStream();

            int exit = new PosSimulator(options, HttpClient.newHttpClient(),
                    new PrintStream(captured, true, StandardCharsets.UTF_8), FIXED).run();

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(exit).as(output).isEqualTo(PosSimulator.EXIT_OK);

            assertThat(services.batches).as("one batch per SKU, in mix order").hasSize(2);
            assertThat(services.batches.get(0))
                    .contains("\"sku\":\"SKU-YOG-1KG\"")
                    .contains("\"quantity\":400")
                    .contains("\"expiryAt\":\"2026-10-04T11:10:00Z\"")
                    .contains("\"unitCost\":{\"amount\":2.10,\"currency\":\"AUD\"}")
                    .contains("\"supplierRef\":\"SIM-1\"");
            assertThat(services.batches.get(1))
                    .contains("\"sku\":\"SKU-STR-250G\"")
                    .contains("\"supplierRef\":\"SIM-2\"");

            assertThat(services.transactions).as("600 a minute for one second").hasSize(10);
            assertThat(services.transactions).allSatisfy(body -> assertThat(body)
                    .contains("\"tillId\":\"TILL-01\"")
                    .contains("\"occurredAt\":\"2026-10-04T10:00:00Z\"")
                    .containsPattern("\"sku\":\"SKU-(YOG-1KG|STR-250G)\"")
                    .containsPattern("\"quantity\":[123]\\}"));

            assertThat(output)
                    .contains("batchId=BAT-SIM-1")
                    .contains("batchId=BAT-SIM-2")
                    .contains("transactionId=TXN-1 ")
                    .contains("transactionId=TXN-10 ")
                    .contains("batches seeded=2/2")
                    .contains("sent=10 created=10 failed=0");
        }
    }

    @Test
    @DisplayName("a response other than 201 is counted as a failure and the exit code says so")
    void nonCreatedResponsesAreCountedAsFailures() throws Exception {
        try (FakeServices services = new FakeServices(400)) {
            PosSimulator.Options options = PosSimulator.Options.parse(
                    "--sales-url=" + services.url(), "--rate=600", "--duration=1");
            ByteArrayOutputStream captured = new ByteArrayOutputStream();

            int exit = new PosSimulator(options, HttpClient.newHttpClient(),
                    new PrintStream(captured, true, StandardCharsets.UTF_8), FIXED).run();

            String output = captured.toString(StandardCharsets.UTF_8);
            assertThat(exit).as(output).isEqualTo(PosSimulator.EXIT_FAILURES);
            assertThat(services.batches).as("--seed-batches defaults to false").isEmpty();
            assertThat(services.transactions).hasSize(10);
            assertThat(output)
                    .contains("HTTP 400")
                    .contains("body={\"error\":\"UNKNOWN_SKU\"")
                    .contains("sent=10 created=0 failed=10");
        }
    }

    @Test
    @DisplayName("a service that refuses the connection ends the run with exit code 2 and the URL")
    void connectionRefusedExitsWithCodeTwoNamingTheUrl() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        String url = "http://localhost:" + closedPort;
        PosSimulator.Options options = PosSimulator.Options.parse(
                "--sales-url=" + url, "--rate=60", "--duration=1");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        int exit = new PosSimulator(options, HttpClient.newHttpClient(),
                new PrintStream(captured, true, StandardCharsets.UTF_8), FIXED).run();

        String output = captured.toString(StandardCharsets.UTF_8);
        assertThat(exit).as(output).isEqualTo(PosSimulator.EXIT_UNREACHABLE);
        assertThat(output).contains("cannot connect to " + url + "/api/transactions");
    }

    /**
     * A stand-in for both services on one port: records every request body and answers with an
     * identifier, 201 for batches and whatever status it was given for transactions.
     */
    private static final class FakeServices implements AutoCloseable {

        private final HttpServer server;
        final List<String> batches = new CopyOnWriteArrayList<>();
        final List<String> transactions = new CopyOnWriteArrayList<>();

        FakeServices(int transactionStatus) throws IOException {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/batches", exchange ->
                    respond(exchange, batches, 201, n -> "{\"batchId\":\"BAT-SIM-" + n + "\"}"));
            server.createContext("/api/transactions", exchange ->
                    respond(exchange, transactions, transactionStatus, n -> transactionStatus == 201
                            ? "{\"transactionId\":\"TXN-" + n + "\"}"
                            : "{\"error\":\"UNKNOWN_SKU\",\"message\":\"No price is in force.\"}"));
            server.start();
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        private static void respond(HttpExchange exchange, List<String> recorded, int status,
                                    IntFunction<String> body) throws IOException {
            recorded.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.apply(recorded.size()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
