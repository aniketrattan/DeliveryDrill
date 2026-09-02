package dev.deliverydrill.model;

import java.util.ArrayList;
import java.util.List;

public class SuiteResult {
    public String suite;
    public String target;
    public long seed;
    public String version = "0.1.0";
    public List<TestResult> tests = new ArrayList<>();

    public long passed() { return tests.stream().filter(t -> t.passed).count(); }
    public long failed() { return tests.size() - passed(); }
    public boolean successful() { return failed() == 0; }
    public boolean targetUnavailable() {
        var deliveries = tests.stream().flatMap(t -> t.deliveries.stream()).toList();
        return !deliveries.isEmpty() && deliveries.stream().allMatch(d -> d.status == 0 && (d.timeout || d.error != null));
    }
}
