package org.chenile.orchestrator.process.outbox;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
  void producerOnlyEnqueuesInterestedCommands() throws Exception {
    var ignoredChecks = new AtomicInteger();
    var ignoredEnqueues = new AtomicInteger();
    var interestedEnqueues = new AtomicInteger();
    OutboxCommandLifecycle<String> interested =
        command("INTERESTED", ignored -> true, interestedEnqueues);
    OutboxCommandLifecycle<String> ignored =
        command(
            "IGNORED",
            context -> {
              ignoredChecks.incrementAndGet();
              return false;
            },
            ignoredEnqueues);
    new OutboxCommandProducer<>(List.of(ignored, interested)).enqueue("context");
    assertEquals(1, ignoredChecks.get());
    assertEquals(0, ignoredEnqueues.get());
    assertEquals(1, interestedEnqueues.get());
  }

  private static OutboxCommandLifecycle<String> command(
      String type, java.util.function.Predicate<String> interested, AtomicInteger enqueues) {
    return new OutboxCommandLifecycle<>() {
      @Override
      public String type() {
        return type;
      }

      @Override
      public boolean shouldEnqueue(String context) {
        return interested.test(context);
      }

      @Override
      public void enqueue(String context) {
        enqueues.incrementAndGet();
      }

      @Override
      public void dispatch(OutboxCommand command) {}
    };
  }
}
