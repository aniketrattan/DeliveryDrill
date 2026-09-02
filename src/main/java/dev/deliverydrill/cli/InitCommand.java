package dev.deliverydrill.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "init", description = "Create a starter scenario and fixture in a directory.")
public final class InitCommand implements Callable<Integer> {
    @Option(names = {"-d", "--directory"}, defaultValue = ".", description = "Directory to initialize") Path directory;
    @Override public Integer call() {
        try {
            Files.createDirectories(directory);
            Path fixture = directory.resolve("event.json");
            Path scenario = directory.resolve("deliverydrill.yml");
            if (Files.exists(scenario) || Files.exists(fixture)) { System.err.println("Refusing to overwrite existing starter files"); return 2; }
            Files.writeString(fixture, "{\n  \"id\": \"example-1\",\n  \"type\": \"example.created\"\n}\n");
            Files.writeString(scenario, "version: 1\nname: starter\ntarget:\n  url: http://localhost:8080/webhooks\n  timeout: 5s\nevents:\n  example:\n    file: event.json\n    event_id: evt-example-1\ntests:\n  - name: normal delivery\n    event: example\n");
            System.out.println("Created " + scenario + " and " + fixture); return 0;
        } catch (Exception e) { System.err.println("Unable to initialize: " + e.getMessage()); return 3; }
    }
}

