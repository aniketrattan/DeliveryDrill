package dev.deliverydrill.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "deliverydrill", mixinStandardHelpOptions = true, version = "DeliveryDrill 0.1.0",
        description = "Resilience and chaos testing for webhook consumers.",
        subcommands = {RunCommand.class, ValidateCommand.class, ListFaultsCommand.class, InitCommand.class, VersionCommand.class})
public final class Main implements Runnable {
    public static void main(String[] args) { System.exit(new CommandLine(new Main()).execute(args)); }
    @Override public void run() { new CommandLine(this).usage(System.out); }
}
