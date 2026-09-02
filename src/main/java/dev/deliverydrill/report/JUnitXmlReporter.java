package dev.deliverydrill.report;

import dev.deliverydrill.model.AssertionResult;
import dev.deliverydrill.model.SuiteResult;
import dev.deliverydrill.model.TestResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JUnitXmlReporter implements Reporter {
    @Override public void write(SuiteResult suite, Path output) throws IOException {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"").append(escape(suite.suite)).append("\" tests=\"").append(suite.tests.size()).append("\" failures=\"").append(suite.failed()).append("\">\n");
        for (TestResult test : suite.tests) {
            xml.append("  <testcase name=\"").append(escape(test.name)).append("\" time=\"").append(test.duration.toMillis() / 1000.0).append("\">");
            if (!test.passed) xml.append("<failure message=\"").append(escape(test.diagnosis == null ? "test failed" : test.diagnosis)).append("\">");
            if (!test.passed) { for (AssertionResult a : test.assertions) if (!a.passed) xml.append(escape(a.name + ": " + a.message)); xml.append("</failure>"); }
            xml.append("</testcase>\n");
        }
        xml.append("</testsuite>\n");
        if (output == null) System.out.print(xml); else { if (output.getParent() != null) Files.createDirectories(output.getParent()); Files.writeString(output, xml.toString()); }
    }
    private static String escape(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;").replace(">", "&gt;"); }
}

