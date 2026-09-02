package dev.deliverydrill.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deliverydrill.model.AssertionResult;
import dev.deliverydrill.model.ScenarioConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AssertionEngine {
    private final HttpClient client;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public AssertionEngine(HttpClient client, Duration timeout) { this.client = client; this.timeout = timeout; }

    public List<AssertionResult> evaluate(List<ScenarioConfig.AssertionConfig> assertions) {
        List<AssertionResult> results = new ArrayList<>();
        for (ScenarioConfig.AssertionConfig assertion : assertions) results.add(evaluate(assertion));
        return results;
    }

    private AssertionResult evaluate(ScenarioConfig.AssertionConfig assertion) {
        String name = assertion.name == null || assertion.name.isBlank() ? "HTTP assertion" : assertion.name;
        if (assertion.request == null || assertion.request.url == null) return AssertionResult.fail(name, "request.url is required");
        Observation response = observe(assertion.request);
        if (response.error() != null) return AssertionResult.fail(name, response.error());
        try {
            ScenarioConfig.ExpectConfig expect = assertion.expect == null ? new ScenarioConfig.ExpectConfig() : assertion.expect;
            if (expect.status != null && response.status() != expect.status) return AssertionResult.fail(name, "expected status " + expect.status + " but received " + response.status());
            if (expect.headers != null) for (Map.Entry<String, String> header : expect.headers.entrySet()) {
                String actual = response.headers().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(header.getKey())).findFirst().flatMap(e -> e.getValue().stream().findFirst()).orElse(null);
                if (actual == null || !actual.equalsIgnoreCase(header.getValue())) return AssertionResult.fail(name, "expected header " + header.getKey() + "=" + header.getValue() + " but received " + actual);
            }
            if (expect.body_contains != null && !response.body().contains(expect.body_contains)) return AssertionResult.fail(name, "response body does not contain '" + expect.body_contains + "'");
            if (expect.json != null && !expect.json.isEmpty()) {
                JsonNode root;
                try { root = mapper.readTree(response.body()); } catch (Exception e) { return AssertionResult.fail(name, "response is not valid JSON: " + e.getMessage()); }
                for (Map.Entry<String, Object> check : expect.json.entrySet()) {
                    JsonNode actual = query(root, check.getKey());
                    JsonNode expected = mapper.valueToTree(check.getValue());
                    if (actual == null || actual.isMissingNode() || !jsonEquals(actual, expected)) return AssertionResult.fail(name, check.getKey() + " expected " + expected + " but received " + (actual == null ? "<missing>" : actual));
                }
            }
            return AssertionResult.pass(name);
        } catch (Exception e) { return AssertionResult.fail(name, "assertion failed: " + e.getMessage()); }
    }

    public Observation observe(ScenarioConfig.RequestConfig request) {
        if (request == null || request.url == null) return new Observation(0, "", Map.of(), "request.url is required");
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url)).timeout(timeout.isZero() ? Duration.ofSeconds(30) : timeout);
            if (request.headers != null) request.headers.forEach(builder::header);
            String method = request.method == null ? "GET" : request.method.toUpperCase();
            if ("GET".equals(method) || "HEAD".equals(method)) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.method(method, HttpRequest.BodyPublishers.ofString(request.body == null ? "" : request.body));
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Observation(response.statusCode(), response.body(), response.headers().map(), null);
        } catch (java.net.http.HttpTimeoutException e) { return new Observation(0, "", Map.of(), "assertion request timed out"); }
        catch (Exception e) { return new Observation(0, "", Map.of(), "assertion request failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())); }
    }

    public JsonNode query(JsonNode root, String expression) {
        if (expression == null || !expression.startsWith("$")) return null;
        String path = expression.substring(1);
        JsonNode current = root;
        if (path.isEmpty()) return current;
        for (String segment : path.split("\\.")) {
            if (segment.isEmpty()) continue;
            if ("length".equals(segment)) { if (!current.isArray() && !current.isObject()) return null; return mapper.getNodeFactory().numberNode(current.size()); }
            if (segment.matches(".+\\[\\d+\\]")) {
                int open = segment.indexOf('['); String field = segment.substring(0, open); int index = Integer.parseInt(segment.substring(open + 1, segment.length() - 1));
                if (!field.isEmpty()) current = current.path(field);
                current = current.path(index);
            } else current = current.path(segment);
            if (current.isMissingNode()) return null;
        }
        return current;
    }

    public JsonNode queryJson(String body, String expression) {
        try { return query(mapper.readTree(body), expression); } catch (Exception e) { return null; }
    }

    private boolean jsonEquals(JsonNode actual, JsonNode expected) {
        if (actual.isNumber() && expected.isTextual()) { try { return actual.asDouble() == Double.parseDouble(expected.textValue()); } catch (NumberFormatException ignored) { } }
        if (actual.isTextual() && expected.isNumber()) return actual.textValue().equals(expected.asText());
        return actual.equals(expected);
    }

    public record Observation(int status, String body, Map<String, List<String>> headers, String error) { }
}
