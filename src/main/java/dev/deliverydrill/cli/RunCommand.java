package dev.deliverydrill.cli;

import dev.deliverydrill.core.DeliveryEngine;
import dev.deliverydrill.core.ScenarioLoader;
import dev.deliverydrill.model.SuiteResult;
import dev.deliverydrill.report.Reporters;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.Callable;

@Command(name = "run", description = "Run a webhook resilience scenario.")
public final class RunCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "YAML scenario file") Path scenario;
    @Option(names = "--seed", description = "Random seed (printed so failed runs can be reproduced)") Long seed;
    @Option(names = "--report", defaultValue = "console", description = "Report format: ${COMPLETION-CANDIDATES}") String report;
    @Option(names = "--output", description = "Write JSON/JUnit report to this path") Path output;
    @Option(names = "--verbose", description = "Print each delivery attempt") boolean verbose;

    @Override public Integer call() {
        try {
            long actualSeed = seed == null ? new SecureRandom().nextLong() : seed;
            ScenarioLoader.LoadedScenario loaded = new ScenarioLoader().load(scenario);
            SuiteResult result = new DeliveryEngine(loaded, actualSeed).run();
            Reporters.create(report, verbose).write(result, output);
            if (result.successful()) return 0;
            return result.targetUnavailable() ? 4 : 1;
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage()); return 2;
        } catch (java.io.IOException e) {
            System.err.println("Configuration error: " + e.getMessage()); return 2;
        } catch (Exception e) {
            System.err.println("DeliveryDrill error: " + e.getMessage()); return 3;
        }
    }
}
