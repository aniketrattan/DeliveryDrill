package dev.deliverydrill.core;

import com.sun.net.httpserver.HttpServer;
import dev.deliverydrill.model.ScenarioConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionEngineTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void evaluatesStatusHeadersAndJsonPath() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/state", exchange -> {
            byte[] body = "{\"status\":\"COMPLETED\",\"transactions\":[1]}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        ScenarioConfig.AssertionConfig assertion = new ScenarioConfig.AssertionConfig(); assertion.name = "state";
        assertion.request = new ScenarioConfig.RequestConfig(); assertion.request.url = "http://localhost:" + server.getAddress().getPort() + "/state";
        assertion.expect.status = 200; assertion.expect.json.put("$.status", "COMPLETED"); assertion.expect.json.put("$.transactions.length", 1);
        assertThat(new AssertionEngine(java.net.http.HttpClient.newHttpClient(), java.time.Duration.ofSeconds(2)).evaluate(List.of(assertion)).getFirst().passed).isTrue();
    }
}

