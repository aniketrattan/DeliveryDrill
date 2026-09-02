package dev.deliverydrill.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.deliverydrill.model.ScenarioConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Loads and validates a scenario before any target request is made. */
public final class ScenarioLoader {
    private final ObjectMapper mapper;

    public ScenarioLoader() {
        mapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public LoadedScenario load(Path path) throws IOException {
        Path config = path.toAbsolutePath().normalize();
        ScenarioConfig scenario = mapper.readValue(Files.readString(config), ScenarioConfig.class);
        validate(scenario, config.getParent() == null ? Path.of(".").toAbsolutePath() : config.getParent());
        return new LoadedScenario(config, scenario);
    }

    private void validate(ScenarioConfig s, Path configDir) {
        if (s.version != 1) throw new IllegalArgumentException("Unsupported configuration version: " + s.version);
        if (s.target == null || s.target.url == null || s.target.url.isBlank()) throw new IllegalArgumentException("target.url is required");
        try {
            java.net.URI target = java.net.URI.create(s.target.url);
            if (!"http".equalsIgnoreCase(target.getScheme()) && !"https".equalsIgnoreCase(target.getScheme())) throw new IllegalArgumentException("target.url must use http or https");
        } catch (IllegalArgumentException e) { throw new IllegalArgumentException("target.url is invalid: " + s.target.url + " (" + e.getMessage() + ")"); }
        if (s.signature != null && s.signature.algorithm != null && !s.signature.algorithm.equalsIgnoreCase("sha256") && !s.signature.algorithm.equalsIgnoreCase("hmac-sha256") && !s.signature.algorithm.equalsIgnoreCase("sha1") && !s.signature.algorithm.equalsIgnoreCase("hmac-sha1")) throw new IllegalArgumentException("Unsupported signature algorithm: " + s.signature.algorithm);
        if (s.signature != null && s.signature.type != null && !s.signature.type.equalsIgnoreCase("hmac") && !s.signature.type.equalsIgnoreCase("none")) throw new IllegalArgumentException("Unsupported signature type: " + s.signature.type);
        if (s.signature != null && s.signature.encoding != null && !s.signature.encoding.equalsIgnoreCase("hex") && !s.signature.encoding.equalsIgnoreCase("base64")) throw new IllegalArgumentException("Unsupported signature encoding: " + s.signature.encoding);
        if (s.events == null || s.events.isEmpty()) throw new IllegalArgumentException("At least one event is required");
        for (Map.Entry<String, ScenarioConfig.EventConfig> e : s.events.entrySet()) {
            if (e.getValue() == null || e.getValue().file == null || e.getValue().file.isBlank()) throw new IllegalArgumentException("events." + e.getKey() + ".file is required");
            if (e.getValue().event_id == null || e.getValue().event_id.isBlank()) throw new IllegalArgumentException("events." + e.getKey() + ".event_id is required");
            Path fixture = configDir.resolve(e.getValue().file).normalize();
            if (!fixture.startsWith(configDir)) throw new IllegalArgumentException("events." + e.getKey() + ".file escapes the scenario directory");
            if (!Files.isRegularFile(fixture)) throw new IllegalArgumentException("events." + e.getKey() + ".file does not exist: " + e.getValue().file);
            try {
                if (!fixture.toRealPath().startsWith(configDir.toRealPath())) throw new IllegalArgumentException("events." + e.getKey() + ".file escapes the scenario directory via a symlink");
            } catch (IOException ex) { throw new IllegalArgumentException("Unable to resolve events." + e.getKey() + ".file: " + ex.getMessage()); }
        }
        if (s.tests == null || s.tests.isEmpty()) throw new IllegalArgumentException("At least one test is required");
        for (int i = 0; i < s.tests.size(); i++) {
            ScenarioConfig.TestConfig t = s.tests.get(i);
            if (t.name == null || t.name.isBlank()) t.name = "test-" + (i + 1);
            if ((t.event == null || t.event.isBlank()) && (t.sequence == null || t.sequence.isEmpty())) throw new IllegalArgumentException("tests[" + i + "] needs event or sequence");
            if (t.event != null && !t.event.isBlank() && !s.events.containsKey(t.event)) throw new IllegalArgumentException("tests[" + i + "].event references unknown event '" + t.event + "'");
            for (String event : t.sequence) if (!s.events.containsKey(event)) throw new IllegalArgumentException("tests[" + i + "].sequence references unknown event '" + event + "'");
            validateFault(t.fault, "tests[" + i + "].fault");
            if (t.faults != null) for (int j = 0; j < t.faults.size(); j++) validateFault(t.faults.get(j), "tests[" + i + "].faults[" + j + "]");
            if (s.signature == null && hasSignatureFault(t)) throw new IllegalArgumentException("tests[" + i + "] tampers or removes a signature but no signature is configured");
        }
        if (s.invariants != null) for (int i = 0; i < s.invariants.size(); i++) {
            ScenarioConfig.InvariantConfig invariant = s.invariants.get(i);
            if (invariant == null || invariant.source == null || invariant.source.url == null || invariant.source.url.isBlank()) throw new IllegalArgumentException("invariants[" + i + "].source.url is required");
            if (invariant.field == null || !invariant.field.startsWith("$")) throw new IllegalArgumentException("invariants[" + i + "].field must be a JSONPath beginning with $");
            if (invariant.order == null || invariant.order.isEmpty()) throw new IllegalArgumentException("invariants[" + i + "].order must not be empty");
        }
    }

    private boolean hasSignatureFault(ScenarioConfig.TestConfig t) {
        if (hasSignatureFault(t.fault)) return true;
        return t.faults != null && t.faults.stream().anyMatch(this::hasSignatureFault);
    }
    private boolean hasSignatureFault(ScenarioConfig.FaultConfig f) { return f != null && (Boolean.TRUE.equals(f.tamper_signature) || Boolean.TRUE.equals(f.missing_signature)); }


    private void validateFault(ScenarioConfig.FaultConfig f, String location) {
        if (f == null) return;
        if (f.duplicate != null && (f.duplicate.count < 1 || f.duplicate.concurrency < 1 || f.duplicate.concurrency > f.duplicate.count)) throw new IllegalArgumentException(location + ".duplicate requires 1 <= concurrency <= count");
        if (f.retry != null && f.retry.attempts < 1) throw new IllegalArgumentException(location + ".retry.attempts must be >= 1");
        if (f.burst != null && (f.burst.events < 1 || f.burst.concurrency < 1 || f.burst.concurrency > f.burst.events)) throw new IllegalArgumentException(location + ".burst requires 1 <= concurrency <= events");
    }

    public record LoadedScenario(Path configPath, ScenarioConfig config) { }
}
