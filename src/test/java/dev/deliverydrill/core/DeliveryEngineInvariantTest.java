package dev.deliverydrill.core;

import com.sun.net.httpserver.HttpServer;
import dev.deliverydrill.model.SuiteResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryEngineInvariantTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void reportsAStateRegressionObservedBetweenEvents() throws Exception {
        AtomicReference<String> state = new AtomicReference<>("CREATED");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/webhooks", exchange -> {
            state.set(exchange.getRequestHeaders().getFirst("X-Event-Type").replace("payment.", "").toUpperCase());
            exchange.sendResponseHeaders(202, -1); exchange.close();
        });
        server.createContext("/payments/P123", exchange -> {
            byte[] body = ("{\"status\":\"" + state.get() + "\"}").getBytes();
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();

        Path dir = Files.createTempDirectory("deliverydrill-invariant");
        Files.writeString(dir.resolve("completed.json"), "{}\n");
        Files.writeString(dir.resolve("processing.json"), "{}\n");
        int port = server.getAddress().getPort();
        Files.writeString(dir.resolve("scenario.yml"), """
                version: 1
                name: invariant-test
                target:
                  url: http://localhost:%d/webhooks
                events:
                  completed:
                    file: completed.json
                    event_id: evt-completed
                    event_type: payment.completed
                  processing:
                    file: processing.json
                    event_id: evt-processing
                    event_type: payment.processing
                tests:
                  - name: regression
                    sequence: [completed, processing]
                invariants:
                  - name: monotonic state
                    source:
                      method: GET
                      url: http://localhost:%d/payments/P123
                    field: $.status
                    order: [CREATED, PROCESSING, COMPLETED]
                """.formatted(port, port));

        SuiteResult result = new DeliveryEngine(new ScenarioLoader().load(dir.resolve("scenario.yml")), 7).run();
        assertThat(result.successful()).isFalse();
        assertThat(result.tests.getFirst().diagnosis).contains("state regressed from COMPLETED to PROCESSING");
    }
}

