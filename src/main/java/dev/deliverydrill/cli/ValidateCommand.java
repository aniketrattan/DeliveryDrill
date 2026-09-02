package dev.deliverydrill.cli;

import dev.deliverydrill.core.ScenarioLoader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "validate", description = "Validate a scenario without contacting its target.")
public final class ValidateCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "YAML scenario file") Path scenario;
    @Override public Integer call() {
        try { ScenarioLoader.LoadedScenario loaded = new ScenarioLoader().load(scenario); System.out.println("Valid scenario: " + loaded.config().name); return 0; }
        catch (Exception e) { System.err.println("Configuration error: " + e.getMessage()); return 2; }
    }
}

