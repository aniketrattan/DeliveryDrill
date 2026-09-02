package dev.deliverydrill.model;

import java.time.Duration;

public class DeliveryResult {
    public String eventName;
    public String eventId;
    public int attempt;
    public int status;
    public Duration duration = Duration.ZERO;
    public boolean timeout;
    public String error;

    public boolean successful() {
        return !timeout && error == null && status >= 200 && status < 300;
    }
}

