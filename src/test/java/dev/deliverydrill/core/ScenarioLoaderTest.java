package dev.deliverydrill.core;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioLoaderTest {
    @Test void validatesUnknownEventBeforeExecution() throws Exception {
        Path dir = Files.createTempDirectory("eg");
        Path file = dir.resolve("scenario.yml");
        Files.writeString(dir.resolve("event.json"), "{}\n");
        Files.writeString(file, "version: 1\ntarget:\n  url: http://localhost:1\nevents:\n  known:\n    file: event.json\n    event_id: e1\ntests:\n  - name: bad\n    event: missing\n");
        assertThatThrownBy(() -> new ScenarioLoader().load(file)).hasMessageContaining("unknown event");
    }
    @Test void loadsValidStarter() throws Exception {
        Path dir = Files.createTempDirectory("eg");
        Path file = dir.resolve("scenario.yml");
        Files.writeString(dir.resolve("event.json"), "{}\n");
        Files.writeString(file, "version: 1\ntarget:\n  url: http://localhost:1\nevents:\n  e:\n    file: event.json\n    event_id: e1\ntests:\n  - event: e\n");
        assertThat(new ScenarioLoader().load(file).config().events).containsKey("e");
    }
    @Test void rejectsFixtureOutsideScenarioDirectory() throws Exception {
        Path dir = Files.createTempDirectory("eg");
        Path file = dir.resolve("scenario.yml");
        Files.writeString(file, "version: 1\ntarget:\n  url: http://localhost:1\nevents:\n  e:\n    file: ../secret.json\n    event_id: e1\ntests:\n  - event: e\n");
        assertThatThrownBy(() -> new ScenarioLoader().load(file)).hasMessageContaining("escapes");
    }
    @Test void rejectsUnknownConfigurationFields() throws Exception {
        Path dir = Files.createTempDirectory("eg");
        Files.writeString(dir.resolve("event.json"), "{}\n");
        Path file = dir.resolve("scenario.yml");
        Files.writeString(file, "version: 1\ntarget:\n  url: http://localhost:1\nevents:\n  e:\n    file: event.json\n    event_id: e1\ntests:\n  - event: e\n    typo_fault: true\n");
        assertThatThrownBy(() -> new ScenarioLoader().load(file)).hasMessageContaining("Unrecognized field");
    }
}
