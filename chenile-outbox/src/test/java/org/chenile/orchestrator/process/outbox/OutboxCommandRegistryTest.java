package org.chenile.orchestrator.process.outbox;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxCommandRegistryTest {
  @Test
  void duplicateAndUnregisteredTypesFailClearly() {
    var command = OutboxTestSupport.command("CUSTOM", ignored -> {});
    assertThrows(
        IllegalArgumentException.class, () -> new OutboxCommandRegistry(List.of(command, command)));
    var registry = new OutboxCommandRegistry(List.of(command));
    assertThrows(IllegalArgumentException.class, () -> registry.resolve("UNKNOWN"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void producerOnlyEnqueuesInterestedCommands() throws Exception {
    OutboxCommandLifecycle<String> interested =
        org.mockito.Mockito.mock(OutboxCommandLifecycle.class);
    OutboxCommandLifecycle<String> ignored = org.mockito.Mockito.mock(OutboxCommandLifecycle.class);
    org.mockito.Mockito.when(interested.shouldEnqueue("context")).thenReturn(true);
    new OutboxCommandProducer<>(List.of(ignored, interested)).enqueue("context");
    org.mockito.Mockito.verify(ignored).shouldEnqueue("context");
    org.mockito.Mockito.verify(ignored, org.mockito.Mockito.never())
        .enqueue(org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.verify(interested).enqueue("context");
  }
}
