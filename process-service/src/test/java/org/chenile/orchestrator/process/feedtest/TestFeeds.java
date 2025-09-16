package org.chenile.orchestrator.process.feedtest;

import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.Constants;
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

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpringConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
public class TestFeeds {
    @Autowired
    StateEntityService<Process> processManager;

    @Autowired
    private ProcessRepository processRepository;

    @Test
    @Order(1)
    public void testWith1File() throws Exception {
        FeedSplitter.numFiles = 1;
        Process process = new Process();
        process.processType = "feed";
        process.id = "FEED1";
        processManager.create(process);
        Process feedProcess = processManager.retrieve(process.id).getMutatedEntity();
        Assert.assertEquals(Constants.PROCESSED_STATE,feedProcess.getCurrentState().getStateId());
        String fileId = process.id + "FILE1";
        Process fileProcess = processManager.retrieve(fileId).getMutatedEntity();
        Assert.assertEquals(Constants.PROCESSED_STATE,fileProcess.getCurrentState().getStateId());
        Process chunkProcess = processManager.retrieve(fileId + "CHUNK1").getMutatedEntity();
        Assert.assertEquals(Constants.PROCESSED_STATE,chunkProcess.getCurrentState().getStateId());

        // Make sure that the all successor processes are created and has been successfully processed.
        List<Process> allPredecessorList= processRepository.findByPredecessorIdIsNotNull();
        for(Process p: allPredecessorList){
            Process successorProcess = processManager.retrieve(p.getId()).getMutatedEntity();
            Assert.assertEquals(Constants.PROCESSED_STATE,successorProcess.getCurrentState().getStateId());
        }

    }

    @Test
    @Order(2)
    public void testWith2Files() throws Exception {
        FeedSplitter.numFiles = 2;
        Process process = new Process();
        process.processType = "feed";
        process.id = "FEED2";
        processManager.create(process);
        // Test if file1 is updated for the feed.
        Process feedProcess = processManager.retrieve(process.id).getMutatedEntity();
        Assert.assertEquals(Constants.PROCESSED_STATE,feedProcess.getCurrentState().getStateId());
        String fileId = process.id + "FILE1";
        // Make sure that the required args are passed for the sub process as well
        String expectedArgs = """
                { "filename" : "file1" }
                """;
        Process fileProcess = processManager.retrieve(fileId).getMutatedEntity();
        Assert.assertEquals(expectedArgs,fileProcess.args);
        Assert.assertEquals(Constants.PROCESSED_STATE,fileProcess.getCurrentState().getStateId());
        Process chunkProcess = processManager.retrieve(fileId + "CHUNK1").getMutatedEntity();
        Assert.assertEquals(Constants.PROCESSED_STATE,chunkProcess.getCurrentState().getStateId());
        // Test if the second file has been processed successfully as well.
        fileId = process.id + "FILE2";
        fileProcess = processManager.retrieve(fileId).getMutatedEntity();
        expectedArgs = """
                { "filename" : "file2" }
                """;
        Assert.assertEquals(expectedArgs,fileProcess.args);
        Assert.assertEquals(Constants.PROCESSED_STATE,fileProcess.getCurrentState().getStateId());
        chunkProcess = processManager.retrieve(fileId + "CHUNK1").getMutatedEntity();
        Assert.assertEquals(Constants.PROCESSED_STATE,chunkProcess.getCurrentState().getStateId());
        // Make sure that the successor process is created and has been successfully processed.
        List<Process> allPredecessorList= processRepository.findByPredecessorIdIsNotNull();
        for(Process p: allPredecessorList){
            Process successorProcess = processManager.retrieve(p.getId()).getMutatedEntity();
            Assert.assertEquals(Constants.PROCESSED_STATE,successorProcess.getCurrentState().getStateId());
        }
    }
}
