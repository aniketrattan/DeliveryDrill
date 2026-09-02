package dev.deliverydrill.model;

public class AssertionResult {
    public String name;
    public boolean passed;
    public String message;

    public static AssertionResult pass(String name) {
        AssertionResult r = new AssertionResult(); r.name = name; r.passed = true; r.message = "ok"; return r;
    }
    public static AssertionResult fail(String name, String message) {
        AssertionResult r = new AssertionResult(); r.name = name; r.passed = false; r.message = message; return r;
    }
}

