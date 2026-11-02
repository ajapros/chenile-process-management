package org.chenile.orchestrator.process.outbox;

import java.util.List;

/**
 * Names the tenants whose outbox the background poller drains.
 *
 * <p>With a tenant-routing {@code DataSource} (for example Chenile's multi-datasource-utils), every
 * tenant has its own {@code chenile_process_outbox} table, and the producer writes into the table
 * of the tenant in the current {@code ContextContainer}. The scheduler thread has no tenant, so it
 * would only ever drain the default tenant's table. {@link TenantAwareOutboxPoller} asks this
 * supplier for the tenants and drains each one with that tenant set before any outbox transaction
 * opens.
 *
 * <p>Return an empty list for a single-tenant deployment: the poller then drains once, in whatever
 * context the scheduler thread has, exactly as before. Called once per polling cycle, so an
 * implementation may return a changing tenant list.
 */
@FunctionalInterface
public interface OutboxTenantSupplier {
  List<String> tenants();

  /** The single-tenant default: no tenant switching. */
  static OutboxTenantSupplier none() {
    return List::of;
  }

  /** A fixed tenant list. Blank entries are ignored. */
  static OutboxTenantSupplier of(List<String> tenants) {
    List<String> fixed = clean(tenants);
    return () -> fixed;
  }

  static List<String> clean(java.util.Collection<?> tenants) {
    if (tenants == null) return List.of();
    return tenants.stream()
        .filter(java.util.Objects::nonNull)
        .map(t -> t.toString().trim())
        .filter(t -> !t.isEmpty())
        .distinct()
        .toList();
  }
}
