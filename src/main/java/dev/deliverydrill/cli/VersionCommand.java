package dev.deliverydrill.cli;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

@Command(name = "version", description = "Show the DeliveryDrill version.")
public final class VersionCommand implements Callable<Integer> {
    @Override public Integer call() { System.out.println("DeliveryDrill 0.1.0"); return 0; }
}

