package dev.deliverydrill.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.deliverydrill.model.SuiteResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonReporter implements Reporter {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);
    @Override public void write(SuiteResult result, Path output) throws IOException {
        String json = mapper.writeValueAsString(result) + System.lineSeparator();
        if (output == null) System.out.print(json); else { if (output.getParent() != null) Files.createDirectories(output.getParent()); Files.writeString(output, json); }
    }
}
