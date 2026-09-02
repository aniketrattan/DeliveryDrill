package dev.deliverydrill.report;

import dev.deliverydrill.model.SuiteResult;

import java.io.IOException;
import java.nio.file.Path;

public interface Reporter {
    void write(SuiteResult result, Path output) throws IOException;
}

