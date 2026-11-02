package org.chenile.orchestrator.process.outbox;

import java.util.List;
import org.chenile.core.context.ContextContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drains the outbox of every tenant named by an {@link OutboxTenantSupplier}.
 *
 * <p>For each tenant the poller clears the thread's {@link ContextContainer}, sets that tenant and
 * runs {@link OutboxDispatcher#poll()}. Because the tenant is in place before the dispatcher opens
 * its claim, handle, acknowledge and failure transactions, a tenant-routing {@code DataSource}
 * hands every one of them a connection to that tenant's database. The handler's state changes and
 * the acknowledgement therefore still commit together, on the tenant that owns the row.
 *
 * <p>A failure while draining one tenant (its database is down, say) is logged and does not stop
 * the other tenants from being drained. The thread's original context is restored after each
 * tenant. With no tenants this is exactly one {@code dispatcher.poll()} in the current context.
 */
public class TenantAwareOutboxPoller {
  private static final Logger logger = LoggerFactory.getLogger(TenantAwareOutboxPoller.class);

  private final OutboxDispatcher dispatcher;
  private final OutboxTenantSupplier tenants;

  public TenantAwareOutboxPoller(OutboxDispatcher dispatcher, OutboxTenantSupplier tenants) {
    this.dispatcher = dispatcher;
    this.tenants = tenants == null ? OutboxTenantSupplier.none() : tenants;
  }

  /** One background polling cycle across all tenants. */
  public void poll() {
    List<String> current = OutboxTenantSupplier.clean(tenants.tenants());
    if (current.isEmpty()) {
      dispatcher.poll();
      return;
    }
    for (String tenant : current) pollTenant(tenant);
  }

  /** Drains one tenant's outbox; failures are logged, not thrown. */
  public void pollTenant(String tenant) {
    var context = ContextContainer.getInstance();
    var saved = context.snapshot();
    try {
      context.clear();
      context.setTenant(tenant);
      dispatcher.poll();
    } catch (Exception failure) {
      logger.error("Process outbox poll failed for tenant {}", tenant, failure);
    } finally {
      context.restore(saved);
    }
  }

  public List<String> tenants() {
    return OutboxTenantSupplier.clean(tenants.tenants());
  }
}
