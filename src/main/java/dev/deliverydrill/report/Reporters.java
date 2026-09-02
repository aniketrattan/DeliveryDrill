package dev.deliverydrill.report;

public final class Reporters {
    private Reporters() { }
    public static Reporter create(String format, boolean verbose) {
        return switch (format == null ? "console" : format.toLowerCase()) {
            case "json" -> new JsonReporter();
            case "junit", "junit-xml", "xml" -> new JUnitXmlReporter();
            case "console" -> new ConsoleReporter(verbose);
            default -> throw new IllegalArgumentException("Unknown report format: " + format + " (use console, json, or junit)");
        };
    }
}

