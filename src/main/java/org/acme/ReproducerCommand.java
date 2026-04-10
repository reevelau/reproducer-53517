package org.acme;

import java.util.concurrent.Callable;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@TopCommand
@Command(
    name = "reproducer-53517",
    description = "Public Quarkus reproducer for Oracle multi-tenant Flyway CLI behavior.",
    mixinStandardHelpOptions = true,
    subcommands = { MigrateDdlCommand.class }
)
public class ReproducerCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }
}
