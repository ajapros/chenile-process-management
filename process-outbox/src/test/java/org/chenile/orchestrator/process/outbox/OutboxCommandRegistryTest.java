package org.chenile.orchestrator.process.outbox;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OutboxCommandRegistryTest {
    @Test void duplicateAndUnregisteredTypesFailClearly() {
        var command = OutboxTestSupport.command("CUSTOM", ignored -> { });
        assertThrows(IllegalArgumentException.class, () -> new OutboxCommandRegistry<>(List.of(command, command)));
        var registry = new OutboxCommandRegistry<>(List.of(command));
        assertThrows(IllegalArgumentException.class, () -> registry.resolve("UNKNOWN"));
    }
}
