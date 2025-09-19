package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.model.Process;
import org.chenile.workflow.api.StateEntityService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Order;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpringConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
public class TestFile {
    @Autowired
    StateEntityService<Process> processManager;
    @Before
    public void setUp(){

    }
    @Test
    @Order(1)
    public void test1() throws Exception {
        Process process = new Process();
        process.processType = "file";
        process.id = "file-process1";
        process = processManager.create(process).getMutatedEntity();
        System.err.println("Current state of file is " + process.getCurrentState().getStateId());
        Process chunkProcess = processManager.retrieve(process.id + "CHUNK1").getMutatedEntity();
        Process fileProcess = processManager.retrieve(process.id).getMutatedEntity();
        Assert.assertEquals("PROCESSED",fileProcess.getCurrentState().getStateId());
    }
}
