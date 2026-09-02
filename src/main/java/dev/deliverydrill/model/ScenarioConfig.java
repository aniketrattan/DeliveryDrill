package dev.deliverydrill.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Root declarative DeliveryDrill configuration. Public fields intentionally make YAML easy to author. */
public class ScenarioConfig {
    public int version = 1;
    public String name = "webhook-resilience";
    public TargetConfig target = new TargetConfig();
    public Map<String, String> headers = new LinkedHashMap<>();
    public SignatureConfig signature;
    public Map<String, EventConfig> events = new LinkedHashMap<>();
    public List<TestConfig> tests = new ArrayList<>();
    public List<AssertionConfig> assertions = new ArrayList<>();
    @JsonProperty("assert")
    public List<AssertionConfig> assertionAlias = new ArrayList<>();
    public List<InvariantConfig> invariants = new ArrayList<>();

    public static class TargetConfig {
        public String url;
        public String method = "POST";
        public String timeout = "5s";
        public boolean followRedirects = false;
    }

    public static class SignatureConfig {
        public String type = "hmac";
        public String algorithm = "sha256";
        public String header = "X-Signature";
        public String encoding = "hex";
        public SecretConfig secret;
    }

    public static class SecretConfig {
        public String env;
        public String value;
    }

    public static class EventConfig {
        public String file;
        public String event_id;
        public String event_type;
        public String timestamp;
        public Map<String, String> headers = new LinkedHashMap<>();
    }

    public static class TestConfig {
        public String name;
        public String event;
        public List<String> sequence = new ArrayList<>();
        public FaultConfig fault;
        public List<FaultConfig> faults = new ArrayList<>();
        public List<AssertionConfig> assertions = new ArrayList<>();
        @JsonProperty("assert")
        public List<AssertionConfig> assertionAlias = new ArrayList<>();
    }

    public static class FaultConfig {
        public DuplicateConfig duplicate;
        public DelayConfig delay;
        public RetryConfig retry;
        public Boolean tamper_signature;
        public Boolean missing_signature;
        public Boolean malformed_json;
        public Boolean reorder;
        public BurstConfig burst;
    }

    public static class DuplicateConfig {
        public int count = 2;
        public int concurrency = 1;
    }

    public static class DelayConfig {
        public String duration;
        public String min;
        public String max;
        public Boolean random;
    }

    public static class RetryConfig {
        public int attempts = 1;
        public BackoffConfig backoff;
    }

    public static class BackoffConfig {
        public String type = "exponential";
        public String initial = "100ms";
        public double multiplier = 2.0;
    }

    public static class BurstConfig {
        public int events;
        public int concurrency = 1;
    }

    public static class AssertionConfig {
        public String name;
        public RequestConfig request;
        public ExpectConfig expect = new ExpectConfig();
    }

    public static class RequestConfig {
        public String method = "GET";
        public String url;
        public Map<String, String> headers = new LinkedHashMap<>();
        public String body;
    }

    public static class ExpectConfig {
        public Integer status;
        public Map<String, Object> json = new LinkedHashMap<>();
        public Map<String, String> headers = new LinkedHashMap<>();
        public String body_contains;
    }

    public static class InvariantConfig {
        public String name;
        public RequestConfig source;
        public String field;
        public List<String> order = new ArrayList<>();
    }
}
