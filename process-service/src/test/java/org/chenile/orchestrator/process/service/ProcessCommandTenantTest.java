package org.chenile.orchestrator.process.service;

import jakarta.persistence.EntityManager;
import org.chenile.core.context.ContextContainer;
import org.chenile.orchestrator.process.outbox.OutboxCommand;
import org.chenile.orchestrator.process.service.outbox.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessCommandTenantTest {
    @Test void flushesJpaCallbacksBeforeRestoringCommandTenant() throws Exception {
        var context=ContextContainer.getInstance(); var snapshot=context.snapshot();
        boolean active=TransactionSynchronizationManager.isActualTransactionActive();
        var manager=mock(EntityManager.class);
        var support=new ProcessCommandSupport(null,null,null,null,null,mock(PlatformTransactionManager.class));
        ReflectionTestUtils.setField(support,"entityManager",manager);
        var command=new AbstractProcessOutboxCommand(support) {
            @Override public String type() { return "TEST"; }
            @Override public void enqueue(ProcessTransition transition) { }
            @Override protected void dispatchCommand(OutboxCommand entry) { assertEquals("worker-tenant",context.getTenant()); }
        };
        doAnswer(call -> { assertEquals("worker-tenant",context.getTenant()); return null; }).when(manager).flush();
        try {
            context.setTenant("caller-tenant"); TransactionSynchronizationManager.setActualTransactionActive(true);
            var entry=OutboxCommand.create("process","TEST","key",null); entry.tenantId="worker-tenant";
            command.dispatch(entry);
            verify(manager).flush(); assertEquals("caller-tenant",context.getTenant());
        } finally { context.restore(snapshot); TransactionSynchronizationManager.setActualTransactionActive(active); }
    }
    @Test void failedCommandRestoresTenantWithoutFlushingPartialWork() {
        var context=ContextContainer.getInstance(); var snapshot=context.snapshot();
        var manager=mock(EntityManager.class);
        var support=new ProcessCommandSupport(null,null,null,null,null,mock(PlatformTransactionManager.class));
        ReflectionTestUtils.setField(support,"entityManager",manager);
        var command=new AbstractProcessOutboxCommand(support) {
            @Override public String type() { return "TEST"; }
            @Override public void enqueue(ProcessTransition transition) { }
            @Override protected void dispatchCommand(OutboxCommand entry) { throw new IllegalStateException("fail"); }
        };
        try {
            context.setTenant("caller-tenant");
            var entry=OutboxCommand.create("process","TEST","key",null); entry.tenantId="worker-tenant";
            assertThrows(IllegalStateException.class,()->command.dispatch(entry));
            verifyNoInteractions(manager); assertEquals("caller-tenant",context.getTenant());
        } finally { context.restore(snapshot); }
    }
}
