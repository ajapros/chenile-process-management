package org.chenile.orchestrator.process.feedtest;

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
public class TestFeeds {
    @Autowired
    StateEntityService<Process> processManager;
    @Before
    public void setUp(){

    }
    @Test
    @Order(1)
    public void testWith1File() throws Exception {
        FeedSplitter.numFiles = 1;
        Process process = new Process();
        process.setProcessType("feed");
        process.id = "FEED1";
        processManager.create(process);
        Process feedProcess = processManager.retrieve(process.id).getMutatedEntity();
        Assert.assertEquals("AWAITING_SUBPROCESS_COMPLETION",feedProcess.getCurrentState().getStateId());
        String fileId = process.id + "FILE1";
        Process fileProcess = processManager.retrieve(fileId).getMutatedEntity();
        Assert.assertEquals("PROCESSED",fileProcess.getCurrentState().getStateId());
        Process chunkProcess = processManager.retrieve(fileId + "CHUNK1").getMutatedEntity();
        Assert.assertEquals("PROCESSED",chunkProcess.getCurrentState().getStateId());
    }

    @Test
    @Order(2)
    public void testWith2Files() throws Exception {
        FeedSplitter.numFiles = 2;
        Process process = new Process();
        process.setProcessType("feed");
        process.id = "FEED2";
        processManager.create(process);
        // Test if file1 is updated for the feed.
        Process feedProcess = processManager.retrieve(process.id).getMutatedEntity();
        Assert.assertEquals("AWAITING_SUBPROCESS_COMPLETION",feedProcess.getCurrentState().getStateId());
        String fileId = process.id + "FILE1";
        // Make sure that the required args are passed for the sub process as well
        String expectedArgs = """
                { "filename" : "file1" }
                """;
        Process fileProcess = processManager.retrieve(fileId).getMutatedEntity();
        Assert.assertEquals(expectedArgs,fileProcess.getArgs());
        Assert.assertEquals("PROCESSED",fileProcess.getCurrentState().getStateId());
        Process chunkProcess = processManager.retrieve(fileId + "CHUNK1").getMutatedEntity();
        Assert.assertEquals("PROCESSED",chunkProcess.getCurrentState().getStateId());
        // Test if the second file has been processed successfully as well.
        fileId = process.id + "FILE2";
        fileProcess = processManager.retrieve(fileId).getMutatedEntity();
        expectedArgs = """
                { "filename" : "file2" }
                """;
        Assert.assertEquals(expectedArgs,fileProcess.getArgs());
        Assert.assertEquals("PROCESSED",fileProcess.getCurrentState().getStateId());
        chunkProcess = processManager.retrieve(fileId + "CHUNK1").getMutatedEntity();
        Assert.assertEquals("PROCESSED",chunkProcess.getCurrentState().getStateId());
    }
}
