package dev.deliverydrill.report;

import dev.deliverydrill.model.AssertionResult;
import dev.deliverydrill.model.DeliveryResult;
import dev.deliverydrill.model.SuiteResult;
import dev.deliverydrill.model.TestResult;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

public final class ConsoleReporter implements Reporter {
    private final boolean verbose;
    public ConsoleReporter(boolean verbose) { this.verbose = verbose; }

    @Override public void write(SuiteResult suite, Path ignored) throws IOException {
        System.out.println("DeliveryDrill " + suite.version);
        System.out.println();
        System.out.println("Target: " + suite.target);
        System.out.println("Scenario: " + suite.suite);
        System.out.println("Random seed: " + suite.seed);
        System.out.println();
        for (TestResult test : suite.tests) {
            System.out.printf("%s %s (%d ms)%n", test.passed ? "✓" : "✗", test.name, test.duration.toMillis());
            if (verbose) for (DeliveryResult delivery : test.deliveries) System.out.printf("  %s attempt %d -> %s (%d ms)%n", delivery.eventId, delivery.attempt, delivery.timeout ? "TIMEOUT" : (delivery.error == null ? Integer.toString(delivery.status) : delivery.error), delivery.duration.toMillis());
            for (AssertionResult assertion : test.assertions) System.out.printf("  %s %s: %s%n", assertion.passed ? "✓" : "✗", assertion.name, assertion.message);
            if (!test.passed && test.diagnosis != null) System.out.println("  " + test.diagnosis);
        }
        System.out.printf("%n%d passed, %d failed%n", suite.passed(), suite.failed());
    }
}

