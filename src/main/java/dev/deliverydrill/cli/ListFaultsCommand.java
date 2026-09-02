package dev.deliverydrill.cli;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

@Command(name = "list-faults", description = "List fault types supported by this release.")
public final class ListFaultsCommand implements Callable<Integer> {
    @Override public Integer call() {
        System.out.println("duplicate              repeated delivery (optionally concurrent)");
        System.out.println("delay                  fixed or seeded random delay");
        System.out.println("retry                  provider-style retry attempts and backoff");
        System.out.println("reorder                reverse a declared event sequence");
        System.out.println("tamper_signature       alter a generated HMAC");
        System.out.println("missing_signature      omit the configured signature header");
        System.out.println("malformed_json         send a malformed request body");
        System.out.println("burst                  send a bounded burst with controlled concurrency");
        return 0;
    }
}

