package dev.deliverydrill.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class TestResult {
    public String name;
    public boolean passed;
    public Duration duration = Duration.ZERO;
    public String diagnosis;
    public List<DeliveryResult> deliveries = new ArrayList<>();
    public List<AssertionResult> assertions = new ArrayList<>();
}

