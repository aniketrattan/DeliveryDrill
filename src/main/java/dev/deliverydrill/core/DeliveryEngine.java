package dev.deliverydrill.core;

import com.fasterxml.jackson.databind.JsonNode;
import dev.deliverydrill.model.DeliveryResult;
import dev.deliverydrill.model.AssertionResult;
import dev.deliverydrill.model.ScenarioConfig;
import dev.deliverydrill.model.SuiteResult;
import dev.deliverydrill.model.TestResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Executes a validated scenario. One event identity can produce many delivery attempts. */
public final class DeliveryEngine {
    private final ScenarioLoader.LoadedScenario loaded;
    private final ScenarioConfig config;
    private final HttpClient client;
    private final Random random;
    private final long seed;
    private final Duration requestTimeout;
    private final Path configDir;

    public DeliveryEngine(ScenarioLoader.LoadedScenario loaded, long seed) {
        this.loaded = loaded;
        this.config = loaded.config();
        this.seed = seed;
        this.random = new Random(seed);
        this.configDir = loaded.configPath().getParent() == null ? Path.of(".").toAbsolutePath() : loaded.configPath().getParent();
        this.requestTimeout = DurationParser.parse(config.target.timeout);
        this.client = HttpClient.newBuilder()
                .connectTimeout(requestTimeout.isZero() ? Duration.ofSeconds(5) : requestTimeout)
                .followRedirects(config.target.followRedirects ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER)
                .build();
    }

    public SuiteResult run() {
        SuiteResult suite = new SuiteResult();
        suite.suite = config.name == null ? "webhook-resilience" : config.name;
        suite.target = config.target.url;
        suite.seed = seed;
        for (ScenarioConfig.TestConfig test : config.tests) suite.tests.add(runTest(test));
        return suite;
    }

    private TestResult runTest(ScenarioConfig.TestConfig test) {
        Instant started = Instant.now();
        TestResult result = new TestResult();
        result.name = test.name;
        ScenarioConfig.FaultConfig fault = mergeFaults(test);
        AssertionEngine assertionEngine = new AssertionEngine(client, requestTimeout);
        List<InvariantProgress> invariantProgress = new ArrayList<>();
        if (config.invariants != null) for (ScenarioConfig.InvariantConfig invariant : config.invariants) invariantProgress.add(new InvariantProgress(invariant));
        List<String> names = new ArrayList<>();
        if (test.sequence != null && !test.sequence.isEmpty()) names.addAll(test.sequence);
        else names.add(test.event);
        if (Boolean.TRUE.equals(fault.reorder)) Collections.reverse(names);

        for (String name : names) {
            ScenarioConfig.EventConfig event = config.events.get(name);
            byte[] body;
            try { body = readFixture(event.file); }
            catch (Exception e) { result.diagnosis = "Fixture error for " + name + ": " + e.getMessage(); break; }
            int count = fault.duplicate == null ? 1 : fault.duplicate.count;
            if (fault.burst != null) count = fault.burst.events;
            int concurrency = fault.duplicate == null ? 1 : fault.duplicate.concurrency;
            if (fault.burst != null) concurrency = fault.burst.concurrency;
            if (fault.delay != null) sleep(delayFor(fault.delay));
            int attempts = fault.retry == null ? 1 : fault.retry.attempts;
            for (int i = 0; i < count; i++) {
                int index = i + 1;
                for (int attempt = 1; attempt <= attempts; attempt++) {
                    if (attempt > 1 && fault.retry.backoff != null) sleep(backoff(fault.retry.backoff, attempt - 1));
                    int deliveryAttempt = attempt;
                    int batchStart = i;
                    int batchSize = Math.min(concurrency, count - batchStart);
                    java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(batchSize);
                    List<CompletableFuture<DeliveryResult>> futures = new ArrayList<>();
                    for (int batchIndex = batchStart; batchIndex < batchStart + batchSize; batchIndex++) {
                        futures.add(dispatch(event, name, body, deliveryAttempt, batchIndex + 1, barrier, fault));
                    }
                    for (CompletableFuture<DeliveryResult> future : futures) {
                        try { result.deliveries.add(future.join()); }
                        catch (Exception e) { DeliveryResult dr = new DeliveryResult(); dr.eventName = name; dr.error = rootMessage(e); result.deliveries.add(dr); }
                    }
                    i = batchStart + batchSize - 1;
                }
            }
            if (result.diagnosis == null && !invariantProgress.isEmpty()) evaluateInvariants(assertionEngine, invariantProgress, result);
        }

        List<ScenarioConfig.AssertionConfig> assertions = new ArrayList<>();
        if (config.assertions != null) assertions.addAll(config.assertions);
        if (config.assertionAlias != null) assertions.addAll(config.assertionAlias);
        if (test.assertions != null) assertions.addAll(test.assertions);
        if (test.assertionAlias != null) assertions.addAll(test.assertionAlias);
        if (result.diagnosis == null && !assertions.isEmpty()) result.assertions.addAll(assertionEngine.evaluate(assertions));
        result.passed = result.diagnosis == null && deliveriesAccepted(result.deliveries, fault) && result.assertions.stream().allMatch(a -> a.passed);
        if (!result.passed && result.diagnosis == null) result.diagnosis = diagnose(result);
        result.duration = Duration.between(started, Instant.now());
        return result;
    }

    private CompletableFuture<DeliveryResult> dispatch(ScenarioConfig.EventConfig event, String name, byte[] body, int attempt, int index, java.util.concurrent.CyclicBarrier barrier, ScenarioConfig.FaultConfig fault) {
        return CompletableFuture.supplyAsync(() -> {
            try { barrier.await(30, TimeUnit.SECONDS); }
            catch (Exception e) { throw new IllegalStateException("concurrent delivery barrier failed", e); }
            return send(event, name, body, attempt, index, fault);
        }, command -> Thread.startVirtualThread(command));
    }

    private DeliveryResult send(ScenarioConfig.EventConfig event, String name, byte[] originalBody, int attempt, int index, ScenarioConfig.FaultConfig fault) {
        byte[] body = Boolean.TRUE.equals(fault.malformed_json) ? "not-json".getBytes(StandardCharsets.UTF_8) : originalBody;
        Map<String, String> headers = new java.util.LinkedHashMap<>(config.headers == null ? Map.of() : config.headers);
        if (event.headers != null) headers.putAll(event.headers);
        headers.putIfAbsent("X-Event-ID", event.event_id);
        if (event.event_type != null) headers.putIfAbsent("X-Event-Type", event.event_type);
        if (config.signature != null && !"none".equalsIgnoreCase(config.signature.type) && !Boolean.TRUE.equals(fault.missing_signature)) {
            String secret = resolveSecret(config.signature.secret);
            String signature = RequestSigner.sign(body, secret, config.signature.algorithm, config.signature.encoding);
            if (Boolean.TRUE.equals(fault.tamper_signature)) signature = tamper(signature);
            headers.put(config.signature.header == null ? "X-Signature" : config.signature.header, signature);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.target.url))
                .timeout(requestTimeout.isZero() ? Duration.ofSeconds(30) : requestTimeout);
        headers.forEach(builder::header);
        String method = config.target.method == null ? "POST" : config.target.method.toUpperCase();
        if ("GET".equals(method) || "HEAD".equals(method)) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        Instant started = Instant.now();
        DeliveryResult result = new DeliveryResult(); result.eventName = name; result.eventId = event.event_id; result.attempt = attempt;
        try {
            HttpResponse<Void> response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            result.status = response.statusCode();
        } catch (java.net.http.HttpTimeoutException e) {
            result.timeout = true; result.error = "request timed out";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); result.error = "interrupted";
        } catch (Exception e) { result.error = rootMessage(e); }
        result.duration = Duration.between(started, Instant.now());
        return result;
    }

    private byte[] readFixture(String fixture) throws IOException {
        Path candidate = configDir.resolve(fixture).normalize();
        if (!candidate.startsWith(configDir)) throw new IOException("fixture path escapes configuration directory");
        return Files.readAllBytes(candidate);
    }

    private ScenarioConfig.FaultConfig mergeFaults(ScenarioConfig.TestConfig test) {
        ScenarioConfig.FaultConfig out = new ScenarioConfig.FaultConfig();
        List<ScenarioConfig.FaultConfig> all = new ArrayList<>();
        if (test.fault != null) all.add(test.fault);
        if (test.faults != null) all.addAll(test.faults);
        for (ScenarioConfig.FaultConfig f : all) {
            if (f.duplicate != null) out.duplicate = f.duplicate;
            if (f.delay != null) out.delay = f.delay;
            if (f.retry != null) out.retry = f.retry;
            if (f.burst != null) out.burst = f.burst;
            if (Boolean.TRUE.equals(f.tamper_signature)) out.tamper_signature = true;
            if (Boolean.TRUE.equals(f.missing_signature)) out.missing_signature = true;
            if (Boolean.TRUE.equals(f.malformed_json)) out.malformed_json = true;
            if (Boolean.TRUE.equals(f.reorder)) out.reorder = true;
        }
        return out;
    }

    private Duration delayFor(ScenarioConfig.DelayConfig delay) {
        if (delay.duration != null) return DurationParser.parse(delay.duration);
        if (delay.min != null && delay.max != null) {
            long min = DurationParser.parse(delay.min).toMillis(), max = DurationParser.parse(delay.max).toMillis();
            return Duration.ofMillis(min + (max <= min ? 0 : random.nextLong(max - min + 1)));
        }
        return Duration.ZERO;
    }

    private Duration backoff(ScenarioConfig.BackoffConfig backoff, int retryNumber) {
        long initial = DurationParser.parse(backoff.initial).toMillis();
        return Duration.ofMillis((long) (initial * Math.pow(backoff.multiplier <= 0 ? 2 : backoff.multiplier, retryNumber - 1)));
    }

    private String resolveSecret(ScenarioConfig.SecretConfig secret) {
        if (secret == null) throw new IllegalArgumentException("signature.secret is required");
        if (secret.env != null) {
            String value = System.getenv(secret.env);
            if (value == null || value.isEmpty()) throw new IllegalArgumentException("Environment variable " + secret.env + " is not set");
            return value;
        }
        if (secret.value != null) return secret.value;
        throw new IllegalArgumentException("signature.secret needs env or value");
    }

    private String tamper(String signature) { if (signature.isEmpty()) return "0"; char last = signature.charAt(signature.length() - 1); return signature.substring(0, signature.length() - 1) + (last == '0' ? '1' : '0'); }
    private void sleep(Duration d) { if (d.isZero() || d.isNegative()) return; try { Thread.sleep(d.toMillis(), d.getNano() % 1_000_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    private String diagnose(TestResult r) {
        long bad = r.deliveries.stream().filter(d -> !d.successful()).count();
        if (bad > 0) return bad + " delivery attempt(s) did not receive a 2xx response";
        return r.assertions.stream().filter(a -> !a.passed).map(a -> a.name + ": " + a.message).findFirst().orElse("test failed");
    }
    private boolean deliveriesAccepted(List<DeliveryResult> deliveries, ScenarioConfig.FaultConfig fault) {
        if (deliveries.isEmpty()) return false;
        if (deliveries.stream().allMatch(DeliveryResult::successful)) return true;
        boolean expectsRejection = Boolean.TRUE.equals(fault.tamper_signature) || Boolean.TRUE.equals(fault.missing_signature) || Boolean.TRUE.equals(fault.malformed_json);
        return expectsRejection && deliveries.stream().allMatch(d -> !d.timeout && d.error == null && d.status >= 400 && d.status < 500);
    }

    private void evaluateInvariants(AssertionEngine assertionEngine, List<InvariantProgress> progress, TestResult result) {
        for (InvariantProgress state : progress) {
            ScenarioConfig.InvariantConfig invariant = state.config;
            String name = invariant.name == null || invariant.name.isBlank() ? "invariant" : invariant.name;
            if (invariant.source == null || invariant.field == null || invariant.order == null || invariant.order.isEmpty()) {
                result.assertions.add(AssertionResult.fail(name, "invariant requires source, field, and a non-empty order"));
                continue;
            }
            AssertionEngine.Observation observation = assertionEngine.observe(invariant.source);
            if (observation.error() != null) { result.assertions.add(AssertionResult.fail(name, observation.error())); continue; }
            JsonNode value = assertionEngine.queryJson(observation.body(), invariant.field);
            if (value == null || value.isMissingNode()) { result.assertions.add(AssertionResult.fail(name, invariant.field + " is missing from the observation")); continue; }
            String actual = value.isTextual() ? value.textValue() : value.toString();
            int rank = indexOfIgnoreCase(invariant.order, actual);
            if (rank < 0) { result.assertions.add(AssertionResult.fail(name, "observed " + actual + " is not in the allowed order " + invariant.order)); continue; }
            if (state.previousRank >= 0 && rank < state.previousRank) result.assertions.add(AssertionResult.fail(name, "state regressed from " + invariant.order.get(state.previousRank) + " to " + actual));
            if (rank > state.previousRank) state.previousRank = rank;
        }
    }

    private int indexOfIgnoreCase(List<String> values, String value) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).equalsIgnoreCase(value)) return i;
        return -1;
    }

    private static final class InvariantProgress {
        private final ScenarioConfig.InvariantConfig config;
        private int previousRank = -1;
        private InvariantProgress(ScenarioConfig.InvariantConfig config) { this.config = config; }
    }
    private static String rootMessage(Throwable e) { Throwable t = e; while (t.getCause() != null) t = t.getCause(); return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(); }
}
