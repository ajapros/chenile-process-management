package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.orchestrator.process.model.StatusUpdatePayload;
import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.api.StateEntityService;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Order;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = {SpringConfig.class, TestChunks.TestConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
public class TestChunks {
    @Autowired
    StateEntityService<Process> processManager;

    private static final List<StatusUpdatePayload> receivedUpdates = new ArrayList<>();

    @TestConfiguration
    static class TestConfig {
        // This mock bean will replace the real "statusUpdateAction" for this test class.
        @Primary
        @Bean
        public AbstractSTMTransitionAction<Process, StatusUpdatePayload> processStatusUpdate() {
            return new AbstractSTMTransitionAction<>() {
                @Override
                public void transitionTo(Process stateEntity, StatusUpdatePayload payload, State startState, String eventId, State endState, STMInternalTransitionInvoker<?> stm, Transition transition) throws Exception {
                    receivedUpdates.add(payload);

                }
            };
        }
    }

    @Before public void setUp(){
        receivedUpdates.clear();
        ChunkExecutor.sendStatusUpdate = false;
   }

   @Test @Order(1)
    public void test1() throws Exception {
        Process process = new Process();
        process.processType = "chunk";
        processManager.create(process);
        Process process1 = processManager.retrieve(process.id).getMutatedEntity();
       Assert.assertEquals("PROCESSED",process1.getCurrentState().getStateId());
    }

    @Test @Order(2)
    public void testChunk_withStatusUpdates() {
        ChunkExecutor.sendStatusUpdate = true;

        // --- EXECUTE ---
        Process process = new Process();
        process.processType = "chunk";
        processManager.create(process);

        // --- ASSERT ---
        // Verify the process completed
        Process finalProcess = processManager.retrieve(process.id).getMutatedEntity();
        Assert.assertEquals("PROCESSED", finalProcess.getCurrentState().getStateId());

        // Verify that status updates WERE received and are correct
        Assert.assertEquals("Two status updates should have been sent.", 2, receivedUpdates.size());

        Assert.assertEquals(50, receivedUpdates.get(0).percentComplete);
        Assert.assertEquals(90, receivedUpdates.get(1).percentComplete);
    }

}
